plugins {
    id(libs.plugins.androidLibrary.get().pluginId)
    id(libs.plugins.jetbrainsKotlinAndroid.get().pluginId)
    alias(libs.plugins.kotlinSerialization)
}

apply<BaseLibConfig>()
apply<NavigationPreset>()

android {
    namespace = "com.salat.navigation"
}

dependencies {
    implementation(project(Modules.FEATURE_PREVIEW))
    implementation(project(Modules.FEATURE_SETTINGS))
    implementation(project(Modules.FEATURE_ARCHIVE))

    implementation(libs.androidx.animation.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
