#!/usr/bin/env python3
import argparse
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Tuple

FILE_MAGIC = b"AVCIDX1\n"
HEADER_SIZE = 64
RECORD_SIZE = 32
RECORD_MAGIC = 0x31445849
FLAG_KEY_FRAME = 1
FLAG_CODEC_CONFIG = 1 << 1
HEADER_STRUCT = struct.Struct("<8sIIIIQ4Q")
RECORD_STRUCT = struct.Struct("<IIIQqI")
TS_PACKET_SIZE = 188
TS_HEADER_SIZE = 4
TS_PAYLOAD_SIZE = TS_PACKET_SIZE - TS_HEADER_SIZE
ADAPTATION_FIELD_LENGTH_BYTES = 1
PCR_ADAPTATION_FIELD_LENGTH = 7
NO_ADAPTATION_FIELD = -1
MPEG_TIMEBASE_HZ = 90_000
PES_STREAM_ID_VIDEO = 0xE0
PAT_PID = 0x0000
PMT_PID = 0x0100
VIDEO_PID = 0x0101
PTS_ONLY_PREFIX = 0x02
PTS_BYTES_LENGTH = 5
TS_CONTINUITY_COUNTER_MASK = 0x0F
TS_ADAPTATION_CONTROL_PAYLOAD_ONLY = 0x10
TS_ADAPTATION_CONTROL_ADAPTATION_AND_PAYLOAD = 0x30
PSI_PAYLOAD_START_MASK = 0x40
TS_PAYLOAD_START_MASK = 0x40
PCR_FLAG_MASK = 0x10
TS_SYNC_BYTE = 0x47
TS_STUFFING_BYTE = 0xFF
ANNEX_B_START_CODE = b"\x00\x00\x00\x01"
AUD_NAL = b"\x00\x00\x00\x01\x09\x10"
PAT_SECTION = bytes([
    0x00,
    0xB0, 0x0D,
    0x00, 0x01,
    0xC1,
    0x00,
    0x00,
    0x00, 0x01,
    0xE1, 0x00,
    0xE8, 0xF9, 0x5E, 0x7D,
])
PMT_SECTION = bytes([
    0x02,
    0xB0, 0x12,
    0x00, 0x01,
    0xC1,
    0x00,
    0x00,
    0xE1, 0x01,
    0xF0, 0x00,
    0x1B,
    0xE1, 0x01,
    0xF0, 0x00,
    0x4F, 0xC4, 0x3D, 0x1B,
])


@dataclass
class IndexHeader:
    declared_frame_rate: int


@dataclass
class IndexRecord:
    flags: int
    sample_size: int
    file_offset: int
    presentation_time_us: int

    @property
    def is_codec_config(self) -> bool:
        return (self.flags & FLAG_CODEC_CONFIG) != 0

    @property
    def is_key_frame(self) -> bool:
        return (self.flags & FLAG_KEY_FRAME) != 0


class TsMuxer:
    def __init__(self, output_path: Path):
        self.output = open(output_path, "wb")
        self.pat_continuity_counter = 0
        self.pmt_continuity_counter = 0
        self.video_continuity_counter = 0
        self.segment_start_pts_us = None
        self.bootstrap_needed = True

    def close(self):
        self.output.close()

    def write_sample(self, sample_bytes: bytes, presentation_time_us: int, codec_config: Optional[bytes]):
        if self.segment_start_pts_us is None:
            self.segment_start_pts_us = presentation_time_us
        relative_pts_us = max(0, presentation_time_us - self.segment_start_pts_us)
        if self.bootstrap_needed:
            self.write_pat()
            self.write_pmt()
        self.write_video_pes(sample_bytes, codec_config if self.bootstrap_needed else None, relative_pts_us)
        self.bootstrap_needed = False

    def write_pat(self):
        self.write_psi_packet(PAT_PID, self.pat_continuity_counter, PAT_SECTION)
        self.pat_continuity_counter = (self.pat_continuity_counter + 1) & TS_CONTINUITY_COUNTER_MASK

    def write_pmt(self):
        self.write_psi_packet(PMT_PID, self.pmt_continuity_counter, PMT_SECTION)
        self.pmt_continuity_counter = (self.pmt_continuity_counter + 1) & TS_CONTINUITY_COUNTER_MASK

    def write_psi_packet(self, pid: int, continuity_counter: int, section: bytes):
        packet = bytearray(TS_PACKET_SIZE)
        packet[0] = TS_SYNC_BYTE
        packet[1] = ((pid >> 8) & 0x1F) | PSI_PAYLOAD_START_MASK
        packet[2] = pid & 0xFF
        packet[3] = TS_ADAPTATION_CONTROL_PAYLOAD_ONLY | (continuity_counter & TS_CONTINUITY_COUNTER_MASK)
        packet[4] = 0x00
        packet[5:5 + len(section)] = section
        if 5 + len(section) < TS_PACKET_SIZE:
            packet[5 + len(section):] = bytes([TS_STUFFING_BYTE]) * (TS_PACKET_SIZE - (5 + len(section)))
        self.output.write(packet)

    def write_video_pes(self, sample_bytes: bytes, codec_config: bytes | None, presentation_time_us: int):
        pts_90k = self.us_to_pts90k(presentation_time_us)
        pes_header = self.build_pes_header(pts_90k)
        payload_parts = [pes_header, AUD_NAL]
        if codec_config:
            payload_parts.append(codec_config)
        payload_parts.append(sample_bytes)
        remaining_payload = sum(len(part) for part in payload_parts)
        current_part_index = 0
        current_part_offset = 0
        first_packet = True

        while remaining_payload > 0:
            use_adaptation_field = first_packet or remaining_payload < TS_PAYLOAD_SIZE
            min_adaptation_field_length = PCR_ADAPTATION_FIELD_LENGTH if first_packet else 0
            if use_adaptation_field:
                payload_size = min(
                    remaining_payload,
                    TS_PACKET_SIZE - TS_HEADER_SIZE - ADAPTATION_FIELD_LENGTH_BYTES - min_adaptation_field_length,
                )
                adaptation_field_length = TS_PACKET_SIZE - TS_HEADER_SIZE - ADAPTATION_FIELD_LENGTH_BYTES - payload_size
            else:
                payload_size = min(remaining_payload, TS_PAYLOAD_SIZE)
                adaptation_field_length = NO_ADAPTATION_FIELD

            packet = bytearray(TS_PACKET_SIZE)
            self.write_ts_header(packet, first_packet, adaptation_field_length)
            packet_offset = TS_HEADER_SIZE
            if use_adaptation_field:
                packet_offset += self.write_adaptation_field(
                    packet,
                    packet_offset,
                    adaptation_field_length,
                    pts_90k if first_packet else None,
                )

            bytes_left_for_packet = payload_size
            while bytes_left_for_packet > 0 and current_part_index < len(payload_parts):
                part_bytes = payload_parts[current_part_index]
                part_remaining = len(part_bytes) - current_part_offset
                copy_length = min(part_remaining, bytes_left_for_packet)
                packet[packet_offset:packet_offset + copy_length] = part_bytes[current_part_offset:current_part_offset + copy_length]
                packet_offset += copy_length
                current_part_offset += copy_length
                bytes_left_for_packet -= copy_length
                remaining_payload -= copy_length
                if current_part_offset >= len(part_bytes):
                    current_part_index += 1
                    current_part_offset = 0

            self.output.write(packet)
            first_packet = False

    def write_ts_header(self, packet: bytearray, payload_start: bool, adaptation_field_length: int):
        packet[0] = TS_SYNC_BYTE
        packet[1] = ((VIDEO_PID >> 8) & 0x1F) | (TS_PAYLOAD_START_MASK if payload_start else 0)
        packet[2] = VIDEO_PID & 0xFF
        adaptation_control = TS_ADAPTATION_CONTROL_PAYLOAD_ONLY if adaptation_field_length == NO_ADAPTATION_FIELD else TS_ADAPTATION_CONTROL_ADAPTATION_AND_PAYLOAD
        packet[3] = adaptation_control | (self.video_continuity_counter & TS_CONTINUITY_COUNTER_MASK)
        self.video_continuity_counter = (self.video_continuity_counter + 1) & TS_CONTINUITY_COUNTER_MASK

    def write_adaptation_field(self, packet: bytearray, offset: int, adaptation_field_length: int, pcr_90k: Optional[int]) -> int:
        packet[offset] = adaptation_field_length & 0xFF
        if adaptation_field_length == 0:
            return ADAPTATION_FIELD_LENGTH_BYTES
        flags_offset = offset + 1
        if pcr_90k is not None:
            packet[flags_offset] = PCR_FLAG_MASK
            self.write_pcr(packet, flags_offset + 1, pcr_90k)
            stuffing_offset = flags_offset + 7
        else:
            packet[flags_offset] = 0x00
            stuffing_offset = flags_offset + 1
        field_end_offset = offset + 1 + adaptation_field_length
        while stuffing_offset < field_end_offset:
            packet[stuffing_offset] = TS_STUFFING_BYTE
            stuffing_offset += 1
        return ADAPTATION_FIELD_LENGTH_BYTES + adaptation_field_length

    def write_pcr(self, packet: bytearray, offset: int, pcr_90k: int):
        pcr_base = pcr_90k & 0x1FFFFFFFF
        packet[offset] = (pcr_base >> 25) & 0xFF
        packet[offset + 1] = (pcr_base >> 17) & 0xFF
        packet[offset + 2] = (pcr_base >> 9) & 0xFF
        packet[offset + 3] = (pcr_base >> 1) & 0xFF
        packet[offset + 4] = (((pcr_base & 0x01) << 7) | 0x7E) & 0xFF
        packet[offset + 5] = 0x00

    def build_pes_header(self, pts_90k: int) -> bytes:
        header = bytearray(14)
        header[0] = 0x00
        header[1] = 0x00
        header[2] = 0x01
        header[3] = PES_STREAM_ID_VIDEO
        header[4] = 0x00
        header[5] = 0x00
        header[6] = 0x80
        header[7] = 0x80
        header[8] = PTS_BYTES_LENGTH
        self.write_pts_bytes(header, 9, PTS_ONLY_PREFIX, pts_90k)
        return bytes(header)

    def write_pts_bytes(self, target: bytearray, offset: int, prefix: int, pts_90k: int):
        pts = pts_90k & 0x1FFFFFFFF
        target[offset] = (((prefix & 0x0F) << 4) | (((pts >> 30) & 0x07) << 1) | 0x01) & 0xFF
        target[offset + 1] = (pts >> 22) & 0xFF
        target[offset + 2] = ((((pts >> 15) & 0x7F) << 1) | 0x01) & 0xFF
        target[offset + 3] = (pts >> 7) & 0xFF
        target[offset + 4] = (((pts & 0x7F) << 1) | 0x01) & 0xFF

    def us_to_pts90k(self, presentation_time_us: int) -> int:
        return (presentation_time_us * MPEG_TIMEBASE_HZ) // 1_000_000


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--ffmpeg")
    return parser.parse_args()


def read_header(index_file) -> IndexHeader:
    header_bytes = index_file.read(HEADER_SIZE)
    if len(header_bytes) < HEADER_SIZE:
        raise ValueError("Index file is too short")
    magic, version, header_size, record_size, declared_frame_rate, _created_epoch_ms, *_ = HEADER_STRUCT.unpack(header_bytes)
    if magic != FILE_MAGIC:
        raise ValueError("Unsupported index magic")
    if version != 1:
        raise ValueError(f"Unsupported index version: {version}")
    if header_size != HEADER_SIZE:
        raise ValueError(f"Unexpected index header size: {header_size}")
    if record_size != RECORD_SIZE:
        raise ValueError(f"Unexpected index record size: {record_size}")
    return IndexHeader(declared_frame_rate=declared_frame_rate)


def iter_records(index_path: Path, h264_size: int):
    with index_path.open("rb") as index_file:
        header = read_header(index_file)
        last_end_offset = 0
        while True:
            chunk = index_file.read(RECORD_SIZE)
            if not chunk:
                break
            if len(chunk) < RECORD_SIZE:
                break
            calc_crc = zlib.crc32(chunk[:-4]) & 0xFFFFFFFF
            record_magic, flags, sample_size, file_offset, presentation_time_us, record_crc = RECORD_STRUCT.unpack(chunk)
            if record_magic != RECORD_MAGIC:
                break
            if record_crc != calc_crc:
                break
            if sample_size <= 0:
                break
            if file_offset < last_end_offset:
                break
            if file_offset + sample_size > h264_size:
                break
            last_end_offset = file_offset + sample_size
            yield header, IndexRecord(
                flags=flags,
                sample_size=sample_size,
                file_offset=file_offset,
                presentation_time_us=presentation_time_us,
            )


def build_recovered_ts(input_path: Path, output_ts: Path) -> Tuple[IndexHeader, int, int]:
    index_path = input_path.with_name(input_path.name + ".idx")
    if not index_path.exists():
        raise FileNotFoundError(f"Index file not found: {index_path}")
    h264_size = input_path.stat().st_size
    header = None
    records: List[IndexRecord] = []
    for record_header, record in iter_records(index_path, h264_size):
        if header is None:
            header = record_header
        records.append(record)
    if header is None:
        with index_path.open("rb") as index_file:
            header = read_header(index_file)
    if not records:
        raise ValueError("No valid index records found")

    codec_config_records = [record for record in records if record.is_codec_config]
    sample_records = [record for record in records if not record.is_codec_config]
    if not sample_records:
        raise ValueError("No valid video samples found")

    written_samples = 0
    repaired_samples = 0
    last_pts_us = None
    pending_codec_config = b""

    with input_path.open("rb") as h264_file:
        muxer = TsMuxer(output_ts)
        try:
            for record in records:
                h264_file.seek(record.file_offset)
                payload = h264_file.read(record.sample_size)
                if len(payload) < record.sample_size:
                    break
                if record.is_codec_config:
                    pending_codec_config = payload
                    continue
                pts_us = record.presentation_time_us
                if pts_us < 0:
                    pts_us = 0
                if last_pts_us is not None and pts_us < last_pts_us:
                    pts_us = last_pts_us
                    repaired_samples += 1
                muxer.write_sample(
                    sample_bytes=payload,
                    presentation_time_us=pts_us,
                    codec_config=pending_codec_config,
                )
                pending_codec_config = b""
                last_pts_us = pts_us
                written_samples += 1
        finally:
            muxer.close()

    if written_samples == 0:
        raise ValueError("No valid samples were written to recovered TS")

    return header, written_samples, repaired_samples


def remux_with_ffmpeg(ffmpeg_exe: str, input_ts: Path, output_path: Path):
    extension = output_path.suffix.lower()
    command = [ffmpeg_exe, "-hide_banner", "-loglevel", "warning", "-y", "-i", str(input_ts), "-c", "copy"]
    if extension == ".mp4":
        command.extend(["-movflags", "+faststart"])
    command.append(str(output_path))
    completed = subprocess.run(command)
    if completed.returncode != 0:
        raise RuntimeError(f"ffmpeg failed with code {completed.returncode}")


def resolve_output_path(output_arg: str) -> Path:
    output_path = Path(output_arg)
    if output_path.suffix:
        return output_path
    return output_path.with_suffix(".mp4")


def main() -> int:
    args = parse_args()
    input_path = Path(args.input)
    output_path = resolve_output_path(args.output)
    if not input_path.exists():
        print(f"[ERROR] Input file not found: {input_path}")
        return 1

    temp_dir = Path(tempfile.mkdtemp(prefix="indexed_h264_"))
    temp_ts = temp_dir / (input_path.stem + ".recovered.ts")
    try:
        header, written_samples, repaired_samples = build_recovered_ts(input_path, temp_ts)
        if output_path.suffix.lower() == ".ts":
            output_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(temp_ts), str(output_path))
        else:
            ffmpeg_exe = args.ffmpeg or shutil.which("ffmpeg")
            if not ffmpeg_exe:
                print("[ERROR] ffmpeg not found")
                return 1
            output_path.parent.mkdir(parents=True, exist_ok=True)
            remux_with_ffmpeg(ffmpeg_exe, temp_ts, output_path)
        print(f"[OK] samples={written_samples} repaired_pts={repaired_samples} declared_fps={header.declared_frame_rate}")
        return 0
    except Exception as exc:
        print(f"[ERROR] {exc}")
        return 1
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
