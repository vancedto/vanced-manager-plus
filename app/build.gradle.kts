import java.io.FileInputStream
import java.util.Properties
import java.io.FileOutputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Function to load version properties
fun loadVersionProps(): Properties {
    val versionPropsFile = project.rootProject.file("version.properties")
    val versionProps = Properties()

    if (versionPropsFile.exists()) {
        versionProps.load(FileInputStream(versionPropsFile))
    } else {
        // Initialize with default values if file doesn't exist
        versionProps["VERSION_CODE"] = "1"
        versionProps["VERSION_NAME_MAJOR"] = "2"
        versionProps["VERSION_NAME_MINOR"] = "0"
        versionProps["VERSION_NAME_PATCH"] = "0"
        versionProps.store(FileOutputStream(versionPropsFile), null)
    }

    return versionProps
}
// Load version properties
val versionProps = loadVersionProps()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)

}

android {
    namespace = "com.revanced.net.revancedmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.revanced.net.revancedmanager"
        minSdk = 26
        targetSdk = 34

        // Use version properties
        versionCode = versionProps["VERSION_CODE"].toString().toInt()
        versionName = "${versionProps["VERSION_NAME_MAJOR"]}.${versionProps["VERSION_NAME_MINOR"]}.${versionProps["VERSION_NAME_PATCH"]}"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }
    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // AppMapper logs through android.util.Log, which is a stub that throws in plain JVM
            // tests. Returning defaults instead lets the mapping itself be tested without pulling
            // in Robolectric or wrapping every log call.
            isReturnDefaultValues = true
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}


tasks.register("incrementVersion") {
    group = "versioning"
    description = "Increments version code and patch version"

    doLast {
        println("[VERSION] Starting version increment process...")
        
        val versionPropsFile = project.rootProject.file("version.properties")
        val versionProps = loadVersionProps()
        
        println("[FILE] Loading version properties from: ${versionPropsFile.absolutePath}")

        // Get current versions
        val currentVersionCode = versionProps["VERSION_CODE"].toString().toInt()
        val currentMajor = versionProps["VERSION_NAME_MAJOR"].toString()
        val currentMinor = versionProps["VERSION_NAME_MINOR"].toString()
        val currentPatch = versionProps["VERSION_NAME_PATCH"].toString().toInt()
        
        println("[INFO] Current version: ${currentMajor}.${currentMinor}.${currentPatch} (Code: ${currentVersionCode})")

        // Increment version code
        val newVersionCode = currentVersionCode + 1
        versionProps["VERSION_CODE"] = newVersionCode.toString()
        println("[UPDATE] Version code: ${currentVersionCode} -> ${newVersionCode}")

        // Increment patch version
        val newPatch = currentPatch + 1
        versionProps["VERSION_NAME_PATCH"] = newPatch.toString()
        println("[UPDATE] Patch version: ${currentPatch} -> ${newPatch}")

        // Save updated properties
        println("[SAVE] Saving updated version properties...")
        versionProps.store(FileOutputStream(versionPropsFile), null)

        val newVersionName = "${currentMajor}.${currentMinor}.${newPatch}"
        println("[SUCCESS] Version successfully incremented!")
        println("[RESULT] New version: ${newVersionName} (Code: ${newVersionCode})")
        println("---------------------------------------------------")
    }
}


tasks.register("revancedRelease") {
    description = "Builds release APK, increments version, and copies to apk directory"

    // Make sure this task runs after assembleRelease
    dependsOn("assembleRelease")

    finalizedBy("incrementVersion")
    doLast {
        println("[RELEASE] Starting ReVanced Release Build Process...")
        println("---------------------------------------------------")
        
        // Get the version name from android config
        val versionName = android.defaultConfig.versionName
        println("[INFO] Build version: ${versionName}")

        // Define source and destination files
        val sourceFile = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
        val destinationDir = project.rootDir.resolve("apk")
        val destinationFile = destinationDir.resolve("vanced.to_revanced_manager_plus_v${versionName}.apk")

        println("[SOURCE] Source APK: ${sourceFile.get().asFile.absolutePath}")
        println("[TARGET] Destination directory: ${destinationDir.absolutePath}")
        println("[FILE] Final APK name: vanced.to_revanced_manager_plus_v${versionName}.apk")

        // Create destination directory if it doesn't exist
        if (!destinationDir.exists()) {
            println("[CREATE] Creating destination directory...")
            destinationDir.mkdirs()
        } else {
            println("[EXISTS] Destination directory already exists")
        }

        // Check if source file exists
        if (!sourceFile.get().asFile.exists()) {
            println("[ERROR] Source APK file not found: ${sourceFile.get().asFile.absolutePath}")
            throw Exception("Source APK file not found")
        }

        println("[COPY] Copying and renaming APK file...")
        // Copy and rename the file
        sourceFile.get().asFile.copyTo(destinationFile, overwrite = true)

        // Get file size information
        val fileSizeBytes = destinationFile.length()
        val fileSizeMB = fileSizeBytes / (1024.0 * 1024.0)

        println("[SUCCESS] RELEASE BUILD COMPLETED SUCCESSFULLY!")
        println("---------------------------------------------------")
        println("[LOCATION] APK Location: ${destinationFile.absolutePath}")
        println("[SIZE] File Size: ${String.format("%.2f", fileSizeMB)} MB (${fileSizeBytes} bytes)")
        println("[VERSION] Version: ${versionName}")
        println("[READY] The release APK is ready for distribution!")
        println("---------------------------------------------------")
    }
}

tasks.register("revancedDebug") {
    description = "Builds debug APK, increments version, and copies to apk directory"

    // Make sure this task runs after assembleDebug
    dependsOn("assembleDebug")

    finalizedBy("incrementVersion")
    doLast {
        println("[DEBUG] Starting ReVanced Debug Build Process...")
        println("---------------------------------------------------")
        
        // Get the version name from android config
        val versionName = android.defaultConfig.versionName
        println("[INFO] Build version: ${versionName}")

        // Define source and destination files
        val sourceFile = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
        val destinationDir = project.rootDir.resolve("apk")
        val destinationFile = destinationDir.resolve("vanced.to_revanced_manager_plus_debug_v${versionName}.apk")

        println("[SOURCE] Source APK: ${sourceFile.get().asFile.absolutePath}")
        println("[TARGET] Destination directory: ${destinationDir.absolutePath}")
        println("[FILE] Final APK name: vanced.to_revanced_manager_plus_debug_v${versionName}.apk")

        // Create destination directory if it doesn't exist
        if (!destinationDir.exists()) {
            println("[CREATE] Creating destination directory...")
            destinationDir.mkdirs()
        } else {
            println("[EXISTS] Destination directory already exists")
        }

        // Check if source file exists
        if (!sourceFile.get().asFile.exists()) {
            println("[ERROR] Source APK file not found: ${sourceFile.get().asFile.absolutePath}")
            throw Exception("Source APK file not found")
        }

        println("[COPY] Copying and renaming debug APK file...")
        // Copy and rename the file
        sourceFile.get().asFile.copyTo(destinationFile, overwrite = true)

        // Get file size information
        val fileSizeBytes = destinationFile.length()
        val fileSizeMB = fileSizeBytes / (1024.0 * 1024.0)

        println("[SUCCESS] DEBUG BUILD COMPLETED SUCCESSFULLY!")
        println("---------------------------------------------------")
        println("[LOCATION] Debug APK Location: ${destinationFile.absolutePath}")
        println("[SIZE] File Size: ${String.format("%.2f", fileSizeMB)} MB (${fileSizeBytes} bytes)")
        println("[VERSION] Version: ${versionName}")
        println("[READY] The debug APK is ready for testing!")
        println("---------------------------------------------------")
    }
}

tasks.register("generateKeystore") {
    group = "security"
    description = "Generates a new keystore file with predefined credentials"

    doLast {
        println("[KEYSTORE] Starting Keystore Generation Process...")
        println("---------------------------------------------------")
        
        // Load keystore properties
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        println("[FILE] Loading keystore properties from: ${keystorePropertiesFile.absolutePath}")
        
        if (!keystorePropertiesFile.exists()) {
            println("[ERROR] Keystore properties file not found: ${keystorePropertiesFile.absolutePath}")
            throw Exception("Keystore properties file not found")
        }
        
        val keystoreProperties = Properties().apply {
            load(FileInputStream(keystorePropertiesFile))
        }

        // Extract keystore details from properties
        val storePassword = keystoreProperties["storePassword"] as String
        val keyPassword = keystoreProperties["keyPassword"] as String
        val keyAlias = keystoreProperties["keyAlias"] as String
        val storeFile = keystoreProperties["storeFile"] as String

        println("[CONFIG] Keystore Configuration:")
        println("   [ALIAS] Key Alias: $keyAlias")
        println("   [FILE] Store File: $storeFile")
        println("   [PASS] Store Password: ${"*".repeat(storePassword.length)}")
        println("   [PASS] Key Password: ${"*".repeat(keyPassword.length)}")

        // Create keystore file in app folder
        val appFolder = project.projectDir
        val keystoreFile = File(appFolder, storeFile)
        
        println("[TARGET] Target keystore location: ${keystoreFile.absolutePath}")

        // Check if keystore already exists
        if (keystoreFile.exists()) {
            println("[WARNING] Keystore file already exists. It will be overwritten.")
        }

        // Prepare the keytool command
        val keyToolCommand = arrayOf(
            "keytool",
            "-genkey",
            "-v",
            "-keystore", keystoreFile.absolutePath,
            "-alias", keyAlias,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-storepass", storePassword,
            "-keypass", keyPassword,
            "-dname", "CN=ReVanced,OU=ReVanced,O=ReVanced,L=Unknown,ST=Unknown,C=US"
        )

        println("[EXECUTE] Executing keytool command...")
        println("   [ALG] Algorithm: RSA")
        println("   [SIZE] Key Size: 2048 bits")
        println("   [VALID] Validity: 10000 days")
        println("   [DN] Distinguished Name: CN=ReVanced,OU=ReVanced,O=ReVanced,L=Unknown,ST=Unknown,C=US")

        try {
            // Execute keytool command
            val process = ProcessBuilder(*keyToolCommand)
                .redirectErrorStream(true)
                .start()

            println("[PROCESS] Running keytool process...")
            
            // Print the output
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> 
                    if (line.isNotBlank()) {
                        println("   [OUTPUT] $line")
                    }
                }
            }

            // Wait for the process to complete
            val exitCode = process.waitFor()
            println("[COMPLETE] Keytool process completed with exit code: $exitCode")

            if (exitCode == 0) {
                val keystoreSize = if (keystoreFile.exists()) {
                    val sizeBytes = keystoreFile.length()
                    "${sizeBytes} bytes"
                } else "Unknown"
                
                println("[SUCCESS] KEYSTORE GENERATED SUCCESSFULLY!")
                println("---------------------------------------------------")
                println("[LOCATION] Keystore Location: ${keystoreFile.absolutePath}")
                println("[SIZE] File Size: $keystoreSize")
                println("[ALIAS] Key Alias: $keyAlias")
                println("[VALIDITY] Validity: 10000 days (~27 years)")
                println("[ALGORITHM] Algorithm: RSA 2048-bit")
                println("[IMPORTANT] Keep your keystore file safe and secure!")
                println("[BACKUP] Make sure to backup this keystore file!")
                println("---------------------------------------------------")
            } else {
                println("[FAILED] KEYSTORE GENERATION FAILED!")
                println("---------------------------------------------------")
                println("Exit code: $exitCode")
                println("Please check the error messages above for more details.")
                throw Exception("Failed to generate keystore. Exit code: $exitCode")
            }
        } catch (e: Exception) {
            println("[ERROR] ERROR DURING KEYSTORE GENERATION!")
            println("---------------------------------------------------")
            println("Error message: ${e.message}")
            println("Please ensure:")
            println("  1. Java keytool is installed and in PATH")
            println("  2. keystore.properties file exists and is properly configured")
            println("  3. You have write permissions to the target directory")
            throw e
        }
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)

    // Compose BOM and related dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Image loading
    implementation(libs.coil.compose)

    // Work Manager
    implementation(libs.androidx.work.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    
    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}