import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id(libs.plugins.androidApplication.get().pluginId)
    id(libs.plugins.jetbrainsKotlinAndroid.get().pluginId)
    id(libs.plugins.compose.compiler.get().pluginId)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

apply<BasePreset>()
apply<ComposePreset>()
apply<HiltPreset>()

android {
    namespace = ProjectConfig.APPLICATION_ID
    compileSdk = ProjectConfig.COMPILE_SDK

    defaultConfig {
        applicationId = ProjectConfig.APPLICATION_ID
        minSdk = ProjectConfig.MIN_SDK
        targetSdk = ProjectConfig.TARGET_SDK
        versionCode = getVersionCode()
        versionName = getVersionName()
        setProperty("archivesBaseName", ProjectConfig.ARCHIVES_BASE_NAME)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("Float", "UI_SCALE", "1f")
    }
    bundle {
        language {
            @Suppress("UnstableApiUsage")
            enableSplit = false
        }
    }
    signingConfigs {
        maybeCreate("dummy").apply {
            storeFile = file("dummy_.jks")
            storePassword = "2FsAYfldnqun5YtT2Yjy"
            keyAlias = "dummy"
            keyPassword = "2FsAYfldnqun5YtT2Yjy"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            manifestPlaceholders.apply {
                put("applicationLabel", "@string/app_label")
                put("usesCleartextTraffic", "false")
            }

            buildConfigField("Float", "UI_SCALE", "1.5f")
        }
        maybeCreate("internal").apply {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-I"

            manifestPlaceholders.apply {
                put("applicationLabel", "${ProjectConfig.APPLICATION_NAME} Internal")
                put("usesCleartextTraffic", "true")
            }
            signingConfig = signingConfigs.getByName("dummy")
        }
        debug {
            isDebuggable = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-D"

            manifestPlaceholders.apply {
                put("applicationLabel", "${ProjectConfig.APPLICATION_NAME} Debug")
                put("usesCleartextTraffic", "true")
            }
            signingConfig = signingConfigs.getByName("dummy")
        }
    }
    compileOptions {
        sourceCompatibility = ProjectConfig.COMPATIBILITY_VERSION
        targetCompatibility = ProjectConfig.COMPATIBILITY_VERSION
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Enable traffic interception
    val extDistManifest = "extras/AndroidManifest.xml"
    sourceSets["internal"].manifest.srcFile(extDistManifest)
    sourceSets["debug"].manifest.srcFile(extDistManifest)

    // Authenticator package resource
    applicationVariants.all {
        outputs.all {
            resValue(
                "string", "package_name", applicationId
            )
        }
    }

    // Automatic signing and assembly
    // 1) Copy and rename the file "_secure.signing.gradle" to "secure.signing.gradle"
    // 2) You can copy it to any location and specify the path to it in the "gradle.properties" file
    // 3) Specify the necessary values to sign all builds of the application
    // 4) Run the command in the terminal "./gradlew prepareRelease"
    // 5) Wait and pick up all builds from "app/build/outputs/apk/" and "app/build/outputs/bundle/"
    // https://www.timroes.de/handling-signing-configs-with-gradle
    if (project.hasProperty("secure.signing") && project.file(project.property("secure.signing") as String).exists()) {
        apply(project.property("secure.signing"))
    }
}

tasks.withType(KotlinCompile::class.java).configureEach {
    compilerOptions {
        jvmTarget.set(ProjectConfig.JVM_TARGET)
    }
}

/* Kotlin Block - makes sure that the KSP Plugin looks at
     the right paths when it comes to generated classes*/
kotlin {
    sourceSets {
        debug {
            kotlin.srcDir("build/generated/ksp/debug/kotlin")
        }
        release {
            kotlin.srcDir("build/generated/ksp/release/kotlin")
        }
    }
}

hilt {
    enableAggregatingTask = false
}

dependencies {
    // All modules that use Hilt to generate classes must be connected
    implementation(project(Modules.CORE_BASE))
    // implementation(project(Modules.CORE_COMMON_CONST))
    implementation(project(Modules.CORE_COROUTINES))
    implementation(project(Modules.CORE_NAVIGATION))
    implementation(project(Modules.CORE_RESOURCES))
    implementation(project(Modules.CORE_UI))
    implementation(project(Modules.CORE_UIKIT))
    implementation(project(Modules.CORE_ECARX_FW))
    implementation(project(Modules.CORE_ECARX_CAR))
    implementation(project(Modules.CORE_ADAPT_API))
    implementation(project(Modules.CORE_CAR_API))
    implementation(project(Modules.CORE_RECORDER))
    implementation(project(Modules.CORE_DRIVE_STORAGE))
    implementation(project(Modules.CORE_PREFERENCES))
    implementation(project(Modules.CORE_SHARED_EVENTS))
    implementation(project(Modules.FEATURE_PREVIEW))

    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    //implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    //implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    "baselineProfile"(project(":baselineprofile"))
    debugImplementation(libs.androidx.ui.test.manifest)

    // Detekt only in app module
    detektPlugins(libs.detekt.formatting)
}

// Detekt config
detekt {
    ignoredBuildTypes = listOf("release")
    config.setFrom(rootProject.file("detekt-rules.yml"))
    source.setFrom(
        rootProject.file("/app/src"),
        rootProject.file("/core/base/src"),
        rootProject.file("/core/commonConst/src"),
        rootProject.file("/core/carApi/src"),
        rootProject.file("/core/coroutines/src"),
        rootProject.file("/core/driveStorage/src"),
        rootProject.file("/core/navigation/src"),
        rootProject.file("/core/preferences/src"),
        rootProject.file("/core/recorder/src"),
        rootProject.file("/core/resources/src"),
        rootProject.file("/core/sharedEvents/src"),
        rootProject.file("/core/ui/src"),
        rootProject.file("/core/uikit/src"),
        rootProject.file("/feature/preview/src")
    )
}
// Launch detekt by every build
tasks.getByPath("preBuild")
    .dependsOn("detekt")

afterEvaluate {
    tasks.named("kspNonMinifiedReleaseKotlin").configure {
        dependsOn("kspReleaseKotlin")
    }
}

// -----------------------------------------------------
// Create all BP and assemble all
// -----------------------------------------------------

tasks.register("prepareAll") {
    dependsOn("generateReleaseBaselineProfile")
    finalizedBy("assembleAllBuilds")
}

// Step 4: Assemble all
tasks.register("assembleAllBuilds").get()
    .dependsOn("assembleRelease")
//    .dependsOn("assembleInternal")

// -----------------------------------------------------
// Create release BP and assemble release
// -----------------------------------------------------

// Step 1: Create release profiles
tasks.register("prepareRelease") {
    dependsOn("generateReleaseBaselineProfile")
    finalizedBy("assembleReleaseBuild")
}

// Step 2: Assemble release
tasks.register("assembleReleaseBuild").get()
    .dependsOn("assembleRelease")

// -----------------------------------------------------
// Create internal BP and assemble internal
// -----------------------------------------------------

// Step 1: Create internal profiles
tasks.register("prepareInternal") {
    dependsOn("generateInternalBaselineProfile")
    finalizedBy("assembleInternalBuild")
}

// Step 2: Assemble internal
tasks.register("assembleInternalBuild").get()
    .dependsOn("assembleInternal")
