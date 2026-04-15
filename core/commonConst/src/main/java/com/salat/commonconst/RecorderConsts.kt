package com.salat.commonconst

val UI_SCALE = if (BuildConfig.DEBUG) 1f else 1.5f

const val RECORDS_ROOT_DIRECTORY_NAME = "_CamRecords"
const val STORAGE_CHECK_INTERVAL_MS = 120_000L
const val STORAGE_CHECK_INITIAL_DELAY_MS = 30_000L
const val MIN_FREE_SPACE_RATIO = 0.10
const val TARGET_FREE_SPACE_RATIO = 0.12

const val MAX_VIDEO_WIDTH = 1_280
const val MAX_VIDEO_HEIGHT = 720
const val PIXELS_720P = 1_280 * 720
const val PIXELS_576P = 1_024 * 576

const val VIDEO_BITRATE_720P = 3_400_000 // 4_000_000 base
const val VIDEO_BITRATE_576P = 2_550_000 // 3_000_000 base
const val VIDEO_BITRATE_480P = 1_700_000 // 2_000_000 base

const val SEGMENT_DURATION_MS = 120_000L // 60_000L base
const val RECORDER_START_ATTEMPTS = 2
const val RECORDER_RETRY_DELAY_MS = 200L
const val BROKEN_FILE_DELETE_THRESHOLD_BYTES = 2_048L

const val MAX_CAMERA_COUNT = 4

const val MIN_CAMERA_FPS = 5
const val MAX_CAMERA_FPS = 30
const val CAMERA_OUTPUT_TYPE_AVC = 0
const val CAMERA_OUTPUT_TYPE_TS = 1
const val DEFAULT_CAMERA_FPS = 20

// Get default fps by camera id
val String.defaultCameraFps: Int
    get() = when (this) {
        "0" -> 15 // left
        "1" -> 15 // right
        "2" -> 20 // front
        "3" -> 15 // back
        else -> DEFAULT_CAMERA_FPS
    }

// Get default condition by camera id
val String.defaultCameraEnable: Boolean
    get() = when (this) {
        "0" -> false // left
        "1" -> false // right
        "2" -> true // front
        "3" -> false // back
        else -> false
    }
