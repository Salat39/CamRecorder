package com.salat.archive.domain.entity

enum class ArchiveFileStatus {
    RENDERABLE,
    INVALID_PATH,
    INVALID_DATE_FOLDER,
    INVALID_CAMERA_FOLDER,
    INVALID_FILE_NAME,
    UNSUPPORTED_EXTENSION,
    INVALID_DURATION,
    INVALID_TIME,
}
