plugins {
    id(libs.plugins.androidLibrary.get().pluginId)
    id(libs.plugins.jetbrainsKotlinAndroid.get().pluginId)
}

apply<BaseLibConfig>()
apply<HiltPreset>()

android {
    namespace = "com.salat.carapi"
}

dependencies {
    implementation(project(Modules.CORE_COROUTINES))
    implementation(project(Modules.CORE_ADAPT_API))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.timber)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
