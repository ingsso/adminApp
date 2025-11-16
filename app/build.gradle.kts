plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.adminapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.adminapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database:21.0.0")
    implementation("com.google.firebase:firebase-messaging-ktx:24.0.3")
    implementation ("com.google.firebase:firebase-storage:20.0.0'")
    implementation("com.google.firebase:firebase-auth:23.0.0")
    implementation ("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0")
    implementation("com.google.firebase:firebase-firestore:25.1.1")
    implementation("com.firebase:geofire-android-common:3.1.0")
    implementation ("com.google.android.gms:play-services-location:21.0.1")

    implementation ("com.kakao.maps.open:android:2.6.0")
    implementation("com.github.Baseflow:PhotoView:2.3.0")

    implementation ("com.github.davidmoten:geo:0.7.4")

}