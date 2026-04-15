plugins {
    id(libs.plugins.androidLibrary.get().pluginId)
    id(libs.plugins.jetbrainsKotlinAndroid.get().pluginId)
}

apply<BaseLibConfig>()
apply<HiltPreset>()

android {
    namespace = "com.salat.preferences"
}

dependencies {
    implementation(project(Modules.CORE_COMMON_CONST))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.timber)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
