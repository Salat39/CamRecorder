package com.salat.archive.data.parser

import com.salat.archive.data.entity.ArchiveParsedFile
import com.salat.archive.domain.entity.ArchiveCameraType
import com.salat.archive.domain.entity.ArchiveDayId
import com.salat.archive.domain.entity.ArchiveFileExtension
import com.salat.archive.domain.entity.ArchiveFileStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal class ArchivePathParser(
    private val recordsRoot: File,
) {

    private companion object {
        private val DATE_FOLDER_FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("d MMMM yyyy", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                    isLenient = false
                }
            }
        }

        private val FILE_NAME_REGEX = Regex(
            pattern = "^(\\d{2}-\\d{2}-\\d{2})_(\\d{2}\\.\\d{2}\\.\\d{2})_(\\d+)(?:_(\\d+))?_([LRFB])\\.(h264|ts)$",
            option = RegexOption.IGNORE_CASE,
        )

        private const val MILLIS_IN_SECOND = 1_000
        private const val SECONDS_IN_MINUTE = 60
        private const val MINUTES_IN_HOUR = 60
    }

    fun parse(file: File): ArchiveParsedFile {
        val relativeSegments = runCatching {
            file.relativeTo(recordsRoot).invariantSeparatorsPath.split('/').filter { it.isNotBlank() }
        }.getOrElse {
            return file.invalid(ArchiveFileStatus.INVALID_PATH)
        }

        if (relativeSegments.size != 3) {
            return file.invalid(ArchiveFileStatus.INVALID_PATH)
        }

        val dayId = parseDay(relativeSegments[0]) ?: return file.invalid(ArchiveFileStatus.INVALID_DATE_FOLDER)
        val cameraType = parseCameraType(
            relativeSegments[1]
        ) ?: return file.invalid(ArchiveFileStatus.INVALID_CAMERA_FOLDER)
        val fileName = relativeSegments[2]
        val match = FILE_NAME_REGEX.matchEntire(fileName) ?: return file.invalid(
            status = if (fileName.substringAfterLast('.', missingDelimiterValue = "").isSupportedExtension()) {
                ArchiveFileStatus.INVALID_FILE_NAME
            } else {
                ArchiveFileStatus.UNSUPPORTED_EXTENSION
            },
            dayId = dayId,
            cameraType = cameraType,
        )

        val startMillisOfDay = parseStartMillisOfDay(match.groupValues[1]) ?: return file.invalid(
            status = ArchiveFileStatus.INVALID_TIME,
            dayId = dayId,
            cameraType = cameraType,
        )
        val durationSec = match.groupValues[3].toIntOrNull()?.takeIf { it > 0 } ?: return file.invalid(
            status = ArchiveFileStatus.INVALID_DURATION,
            dayId = dayId,
            cameraType = cameraType,
        )
        val fps = match.groupValues[4].takeIf { it.isNotBlank() }?.toIntOrNull()
        val extension = match.groupValues[6].toArchiveFileExtension() ?: return file.invalid(
            status = ArchiveFileStatus.UNSUPPORTED_EXTENSION,
            dayId = dayId,
            cameraType = cameraType,
        )

        return ArchiveParsedFile(
            absolutePath = file.absolutePath,
            fileName = file.name,
            dayId = dayId,
            cameraType = cameraType,
            extension = extension,
            durationSec = durationSec,
            fps = fps,
            startMillisOfDay = startMillisOfDay,
            status = ArchiveFileStatus.RENDERABLE,
        )
    }

    private fun File.invalid(
        status: ArchiveFileStatus,
        dayId: ArchiveDayId? = null,
        cameraType: ArchiveCameraType? = null,
    ) = ArchiveParsedFile(
        absolutePath = absolutePath,
        fileName = name,
        dayId = dayId,
        cameraType = cameraType,
        extension = null,
        durationSec = null,
        fps = null,
        startMillisOfDay = null,
        status = status,
    )

    private fun parseDay(raw: String): ArchiveDayId? {
        val date = DATE_FOLDER_FORMATTER.get()?.parse(raw) ?: return null
        return ArchiveDayId.from(date)
    }

    private fun parseCameraType(raw: String): ArchiveCameraType? = when (raw.lowercase(Locale.US)) {
        "left" -> ArchiveCameraType.LEFT
        "right" -> ArchiveCameraType.RIGHT
        "front" -> ArchiveCameraType.FRONT
        "back" -> ArchiveCameraType.BACK
        else -> null
    }

    private fun parseStartMillisOfDay(raw: String): Int? {
        val parts = raw.split('-')
        if (parts.size != 3) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        val seconds = parts[2].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59 || seconds !in 0..59) return null
        return (
            (hours * MINUTES_IN_HOUR * SECONDS_IN_MINUTE) +
                (minutes * SECONDS_IN_MINUTE) + seconds
            ) * MILLIS_IN_SECOND
    }

    private fun String.isSupportedExtension(): Boolean = toArchiveFileExtension() != null

    private fun String.toArchiveFileExtension(): ArchiveFileExtension? = when (lowercase(Locale.US)) {
        "h264" -> ArchiveFileExtension.H264
        "ts" -> ArchiveFileExtension.TS
        else -> null
    }
}
