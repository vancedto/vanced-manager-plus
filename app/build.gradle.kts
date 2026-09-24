import java.util.Properties

fun loadProps(file: File): Properties = Properties().apply {
    if (file.exists()) file.inputStream().use { load(it) }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = loadProps(keystorePropertiesFile)

val versionPropsFile = rootProject.file("version.properties")

// Function to load version properties
fun loadVersionProps(versionPropsFile: File): Properties {
    if (versionPropsFile.exists()) return loadProps(versionPropsFile)

    // Initialize with default values if file doesn't exist
    val versionProps = Properties()
    versionProps["VERSION_CODE"] = "1"
    versionProps["VERSION_NAME_MAJOR"] = "2"
    versionProps["VERSION_NAME_MINOR"] = "0"
    versionProps["VERSION_NAME_PATCH"] = "0"
    versionPropsFile.outputStream().use { versionProps.store(it, null) }
    return versionProps
}
// Load version properties
val versionProps = loadVersionProps(versionPropsFile)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)

}

android {
    namespace = "com.revanced.net.revancedmanager"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.revanced.net.revancedmanager"
        minSdk = 26
        targetSdk = 34

        // Use version properties
        versionCode = versionProps["VERSION_CODE"].toString().toInt()
        versionName = "${versionProps["VERSION_NAME_MAJOR"]}.${versionProps["VERSION_NAME_MINOR"]}.${versionProps["VERSION_NAME_PATCH"]}"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}


tasks.register("incrementVersion") {
    group = "versioning"
    description = "Increments version code and patch version"

    // Resolved while configuring: touching Task.project (or the build script) inside doLast is
    // deprecated, fails in Gradle 10 and rules out the configuration cache.
    val propsFile = versionPropsFile

    doLast {
        println("[VERSION] Starting version increment process...")
        println("[FILE] Loading version properties from: ${propsFile.absolutePath}")

        val versionProps = Properties().apply { propsFile.inputStream().use { load(it) } }

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
        propsFile.outputStream().use { versionProps.store(it, null) }

        val newVersionName = "${currentMajor}.${currentMinor}.${newPatch}"
        println("[SUCCESS] Version successfully incremented!")
        println("[RESULT] New version: ${newVersionName} (Code: ${newVersionCode})")
        println("---------------------------------------------------")
    }
}


// Builds the variant's APK, copies it into apk/ under a versioned name, then bumps the version.
// The debug variant bumps too on purpose: every test build gets a higher versionCode, so it
// installs over the previous one on the test device.
fun registerRevancedBuild(taskName: String, variant: String, label: String, apkPrefix: String) {
    // Captured at configuration time so doLast doesn't reach back into the android extension
    val versionName = android.defaultConfig.versionName
    val sourceFile = layout.buildDirectory.file("outputs/apk/$variant/app-$variant.apk")
    val destinationDir = project.rootDir.resolve("apk")
    val apkName = "${apkPrefix}v${versionName}.apk"

    tasks.register(taskName) {
        description = "Builds $variant APK, increments version, and copies to apk directory"
        dependsOn("assemble${variant.replaceFirstChar { it.uppercase() }}")
        finalizedBy("incrementVersion")

        doLast {
            println("[$label] Starting ReVanced $variant build process...")
            println("---------------------------------------------------")
            println("[INFO] Build version: $versionName")

            val source = sourceFile.get().asFile
            val destinationFile = destinationDir.resolve(apkName)
            println("[SOURCE] Source APK: ${source.absolutePath}")
            println("[TARGET] Destination directory: ${destinationDir.absolutePath}")
            println("[FILE] Final APK name: $apkName")

            if (!source.exists()) {
                println("[ERROR] Source APK file not found: ${source.absolutePath}")
                throw GradleException("Source APK file not found")
            }

            destinationDir.mkdirs()
            println("[COPY] Copying and renaming APK file...")
            source.copyTo(destinationFile, overwrite = true)

            val fileSizeBytes = destinationFile.length()
            val fileSizeMB = fileSizeBytes / (1024.0 * 1024.0)
            println("[SUCCESS] ${variant.uppercase()} BUILD COMPLETED SUCCESSFULLY!")
            println("---------------------------------------------------")
            println("[LOCATION] APK Location: ${destinationFile.absolutePath}")
            println("[SIZE] File Size: ${String.format("%.2f", fileSizeMB)} MB (${fileSizeBytes} bytes)")
            println("[VERSION] Version: $versionName")
            println("---------------------------------------------------")
        }
    }
}

registerRevancedBuild("revancedRelease", "release", "RELEASE", "vanced.to_revanced_manager_plus_")
registerRevancedBuild("revancedDebug", "debug", "DEBUG", "vanced.to_revanced_manager_plus_debug_")

tasks.register("generateKeystore") {
    group = "security"
    description = "Generates a new keystore file with predefined credentials"

    // Resolved while configuring, same reason as incrementVersion
    val propsFile = keystorePropertiesFile
    val appFolder = projectDir

    doLast {
        println("[KEYSTORE] Starting Keystore Generation Process...")
        println("---------------------------------------------------")
        
        // Load keystore properties
        println("[FILE] Loading keystore properties from: ${propsFile.absolutePath}")
        
        if (!propsFile.exists()) {
            println("[ERROR] Keystore properties file not found: ${propsFile.absolutePath}")
            throw Exception("Keystore properties file not found")
        }
        
        val keystoreProperties = Properties().apply { propsFile.inputStream().use { load(it) } }

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
    implementation(libs.androidx.core)
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
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

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