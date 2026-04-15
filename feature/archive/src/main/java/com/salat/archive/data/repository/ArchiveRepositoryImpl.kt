package com.salat.archive.data.repository

import com.salat.archive.data.entity.ArchiveParsedFile
import com.salat.archive.data.parser.ArchivePathParser
import com.salat.archive.data.scanner.ArchiveFileScanner
import com.salat.archive.domain.entity.ArchiveCameraTrack
import com.salat.archive.domain.entity.ArchiveCameraType
import com.salat.archive.domain.entity.ArchiveContent
import com.salat.archive.domain.entity.ArchiveDay
import com.salat.archive.domain.entity.ArchiveDayId
import com.salat.archive.domain.entity.ArchiveInvalidFile
import com.salat.archive.domain.entity.ArchiveRecord
import com.salat.archive.domain.entity.ArchiveSegment
import com.salat.archive.domain.repository.ArchiveRepository
import com.salat.drivestorage.domain.repository.DriveStorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ArchiveRepositoryImpl(
    private val driveStorageRepository: DriveStorageRepository,
) : ArchiveRepository {

    private companion object {
        private const val MILLIS_PER_SECOND = 1_000
        private const val MILLIS_PER_MINUTE = 60_000
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1_000
    }

    private val scanner = ArchiveFileScanner(driveStorageRepository)

    override suspend fun getArchiveContent(): ArchiveContent = withContext(Dispatchers.IO) {
        val recordsRoot = scanner.getRecordsRootOrNull() ?: return@withContext ArchiveContent(
            days = emptyList(),
            invalidFiles = emptyList(),
        )
        val parser = ArchivePathParser(recordsRoot)
        val files = runCatching { scanner.scanFiles(recordsRoot) }
            .getOrElse {
                return@withContext ArchiveContent(days = emptyList(), invalidFiles = emptyList())
            }
        val parsed = files.map { parser.parse(it) }
        buildArchiveContent(parsed)
    }

    private fun buildArchiveContent(parsedFiles: List<ArchiveParsedFile>): ArchiveContent {
        if (parsedFiles.isEmpty()) {
            return ArchiveContent(days = emptyList(), invalidFiles = emptyList())
        }

        val invalidFiles = parsedFiles
            .filterNot { it.status == com.salat.archive.domain.entity.ArchiveFileStatus.RENDERABLE }
            .map { parsed ->
                ArchiveInvalidFile(
                    id = parsed.absolutePath,
                    absolutePath = parsed.absolutePath,
                    fileName = parsed.fileName,
                    dayId = parsed.dayId,
                    cameraType = parsed.cameraType,
                    status = parsed.status,
                )
            }

        val validRecords = parsedFiles
            .asSequence()
            .filter { it.status == com.salat.archive.domain.entity.ArchiveFileStatus.RENDERABLE }
            .mapNotNull { parsed ->
                val dayId = parsed.dayId ?: return@mapNotNull null
                val cameraType = parsed.cameraType ?: return@mapNotNull null
                val extension = parsed.extension ?: return@mapNotNull null
                val durationSec = parsed.durationSec ?: return@mapNotNull null
                val startMillisOfDay = parsed.startMillisOfDay ?: return@mapNotNull null
                ArchiveRecord(
                    id = parsed.absolutePath,
                    absolutePath = parsed.absolutePath,
                    fileName = parsed.fileName,
                    folderDayId = dayId,
                    cameraType = cameraType,
                    extension = extension,
                    durationSec = durationSec,
                    fps = parsed.fps,
                    startMillisOfDay = startMillisOfDay,
                    endMillisOfDay = startMillisOfDay + (durationSec * MILLIS_PER_SECOND),
                )
            }
            .toList()

        val dayGroups = linkedMapOf<ArchiveDayId, MutableMap<ArchiveCameraType, MutableList<ArchiveRecord>>>()
        validRecords.forEach { record ->
            splitRecordByDays(record).forEach { slice ->
                val cameras = dayGroups.getOrPut(slice.dayId) { linkedMapOf() }
                val records = cameras.getOrPut(slice.cameraType) { mutableListOf() }
                records += slice.record
            }
        }

        val days = dayGroups
            .mapNotNull { (dayId, cameraRecords) ->
                val tracks = cameraOrder()
                    .mapNotNull { cameraType ->
                        val records = cameraRecords[cameraType].orEmpty().sortedBy { it.startMillisOfDay }
                        if (records.isEmpty()) return@mapNotNull null
                        ArchiveCameraTrack(
                            cameraType = cameraType,
                            records = records,
                            segments = mergeSegments(records, cameraType),
                        )
                    }
                if (tracks.isEmpty()) {
                    null
                } else {
                    ArchiveDay(
                        id = dayId,
                        tracks = tracks,
                    )
                }
            }
            .sortedByDescending { it.id }

        return ArchiveContent(
            days = days,
            invalidFiles = invalidFiles,
        )
    }

    private fun splitRecordByDays(record: ArchiveRecord): List<RecordSlice> {
        val result = mutableListOf<RecordSlice>()
        var currentDay = record.folderDayId
        var currentStart = record.startMillisOfDay
        var remaining = (record.durationSec * MILLIS_PER_SECOND).coerceAtLeast(MILLIS_PER_SECOND)

        while (remaining > 0) {
            val dayRemaining = MILLIS_PER_DAY - currentStart
            val partDuration = minOf(remaining, dayRemaining)
            val partEnd = currentStart + partDuration
            result += RecordSlice(
                dayId = currentDay,
                cameraType = record.cameraType,
                record = record.copy(
                    startMillisOfDay = currentStart,
                    endMillisOfDay = partEnd,
                ),
            )
            remaining -= partDuration
            currentDay = currentDay.nextDay()
            currentStart = 0
        }

        return result
    }

    private fun mergeSegments(records: List<ArchiveRecord>, cameraType: ArchiveCameraType): List<ArchiveSegment> {
        if (records.isEmpty()) return emptyList()
        val sorted = records.sortedWith(compareBy(ArchiveRecord::startMillisOfDay, ArchiveRecord::endMillisOfDay))
        val merged = mutableListOf<MutableSegment>()
        sorted.forEach { record ->
            val current = merged.lastOrNull()
            if (current == null) {
                merged += MutableSegment.from(record)
                return@forEach
            }
            val gap = record.startMillisOfDay - current.endMillisOfDay
            if (gap < MILLIS_PER_MINUTE) {
                merged[merged.lastIndex] = current.copy(
                    endMillisOfDay = maxOf(current.endMillisOfDay, record.endMillisOfDay),
                    sourceRecordIds = current.sourceRecordIds + record.id,
                    sourceFilePaths = current.sourceFilePaths + record.absolutePath,
                )
            } else {
                merged += MutableSegment.from(record)
            }
        }

        return merged.map { mergedSegment ->
            ArchiveSegment(
                id = buildString {
                    append(cameraType.name)
                    append('_')
                    append(mergedSegment.startMillisOfDay)
                    append('_')
                    append(mergedSegment.endMillisOfDay)
                },
                cameraType = cameraType,
                startMillisOfDay = mergedSegment.startMillisOfDay,
                endMillisOfDay = mergedSegment.endMillisOfDay,
                sourceRecordIds = mergedSegment.sourceRecordIds.distinct(),
                sourceFilePaths = mergedSegment.sourceFilePaths.distinct(),
            )
        }
    }

    private fun cameraOrder(): List<ArchiveCameraType> = listOf(
        ArchiveCameraType.LEFT,
        ArchiveCameraType.RIGHT,
        ArchiveCameraType.FRONT,
        ArchiveCameraType.BACK,
    )

    private data class RecordSlice(
        val dayId: ArchiveDayId,
        val cameraType: ArchiveCameraType,
        val record: ArchiveRecord,
    )

    private data class MutableSegment(
        val startMillisOfDay: Int,
        val endMillisOfDay: Int,
        val sourceRecordIds: List<String>,
        val sourceFilePaths: List<String>,
    ) {
        companion object {
            fun from(record: ArchiveRecord) = MutableSegment(
                startMillisOfDay = record.startMillisOfDay,
                endMillisOfDay = record.endMillisOfDay,
                sourceRecordIds = listOf(record.id),
                sourceFilePaths = listOf(record.absolutePath),
            )
        }
    }
}
