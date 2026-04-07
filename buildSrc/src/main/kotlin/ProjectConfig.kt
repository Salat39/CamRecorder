import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

object ProjectConfig {
    const val APPLICATION_NAME = "CamRecorder"
    const val APPLICATION_ID = "com.salat.camrec"

    const val MIN_SDK = 24
    const val TARGET_SDK = 34
    const val COMPILE_SDK = 34

    const val VERSION_MAJOR = 1
    const val VERSION_MINOR = 0
    const val VERSION_PATCH = 0
    const val VERSION_FIX = 0

    const val VERSION_POSTFIX =
        ""
    // "-Beta"
    // "-Direct"

    val ARCHIVES_BASE_NAME = "${getVersionName()}[${getVersionCode()}]CamRecorder"

    val JVM_TARGET = JvmTarget.JVM_17

    val COMPATIBILITY_VERSION = JavaVersion.VERSION_17
}
