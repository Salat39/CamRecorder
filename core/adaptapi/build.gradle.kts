plugins {
    id(libs.plugins.androidLibrary.get().pluginId)
    id(libs.plugins.jetbrainsKotlinAndroid.get().pluginId)
}

apply<BaseLibConfig>()

android {
    namespace = "com.ecarx.xui.adaptapi"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(Modules.CORE_ECARX_FW))
    implementation(project(Modules.CORE_ECARX_CAR))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.timber)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
