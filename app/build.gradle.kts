@file:Suppress("UnstableApiUsage")

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateStationDetailsTask : DefaultTask() {
    @get:InputFile
    abstract val sourceFile: RegularFileProperty

    @get:InputFile
    abstract val placeNamesFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val root = JsonSlurper().parse(sourceFile.get().asFile) as? Map<*, *>
            ?: error("Invalid station metadata root")
        val schemaVersion = (root["schemaVersion"] as? Number)?.toInt()
            ?: error("Missing station metadata schemaVersion")
        val stations = root["stations"] as? List<*>
            ?: error("Missing station metadata stations")
        require(stations.size == 4_360) {
            "Expected 4,360 station metadata records; found ${stations.size}"
        }

        fun Map<*, *>.text(name: String): String? = this[name]
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val placeNamesRoot = JsonSlurper().parse(placeNamesFile.get().asFile) as? Map<*, *>
            ?: error("Invalid JMA place-name root")
        val epicenterNames = placeNamesRoot["epicenter"] as? Map<*, *>
            ?: error("Missing JMA epicenter names")
        val reportingAreaEnglishOverrides = mapOf(
            "北海道奥尻島" to "Okushiri Island, Hokkaido",
            "北海道利尻礼文" to "Rishiri and Rebun Islands, Hokkaido",
            "神津島" to "Kozushima Island",
            "伊豆大島" to "Izu-Oshima Island",
            "新島" to "Niijima Island",
            "三宅島" to "Miyakejima Island",
            "八丈島" to "Hachijojima Island",
            "新潟県佐渡" to "Sado, Niigata Prefecture",
            "兵庫県淡路島" to "Awajishima Island, Hyogo Prefecture",
            "島根県隠岐" to "Oki Islands, Shimane Prefecture",
            "長崎県対馬" to "Tsushima Island, Nagasaki Prefecture",
            "長崎県壱岐" to "Iki Island, Nagasaki Prefecture",
            "長崎県五島" to "Goto Islands, Nagasaki Prefecture",
            "鹿児島県十島村" to "Toshima Village, Kagoshima Prefecture",
            "鹿児島県甑島" to "Koshikishima Islands, Kagoshima Prefecture",
            "鹿児島県種子島" to "Tanegashima Island, Kagoshima Prefecture",
            "鹿児島県屋久島" to "Yakushima Island, Kagoshima Prefecture",
            "鹿児島県奄美北部" to "Northern Amami, Kagoshima Prefecture",
            "鹿児島県奄美南部" to "Southern Amami, Kagoshima Prefecture",
            "沖縄県本島北部" to "Northern Okinawa Main Island, Okinawa Prefecture",
            "沖縄県本島中南部" to "Central and Southern Okinawa Main Island, Okinawa Prefecture",
            "沖縄県久米島" to "Kumejima Island, Okinawa Prefecture",
            "沖縄県大東島" to "Daitojima Islands, Okinawa Prefecture",
            "沖縄県宮古島" to "Miyakojima Island, Okinawa Prefecture",
            "沖縄県石垣島" to "Ishigakijima Island, Okinawa Prefecture",
            "沖縄県与那国島" to "Yonagunijima Island, Okinawa Prefecture",
            "沖縄県西表島" to "Iriomotejima Island, Okinawa Prefecture"
        )
        fun reportingAreaEnglishName(japanese: String): String =
            reportingAreaEnglishOverrides[japanese]
                ?: epicenterNames[japanese]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: epicenterNames["${japanese}地方"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("No English name for JMA reporting area $japanese")
        fun containsJapanese(value: String): Boolean = value.any { character ->
            character in '\u3040'..'\u30ff' || character in '\u3400'..'\u9fff'
        }

        val details = linkedMapOf<String, List<Any?>>()
        val reportingAreas = linkedMapOf<String, List<String>>()
        val municipalityParents = linkedMapOf<String, List<String>>()
        stations.forEach { rawStation ->
            val station = rawStation as? Map<*, *>
                ?: error("Invalid station metadata record")
            val code = station.text("code")
                ?: error("Station metadata record has no code")
            require(code.matches(Regex("\\d{7}"))) { "Invalid station code $code" }
            require(details[code] == null) { "Duplicate station code $code" }
            details[code] = listOf(
                station.text("publishedAddressJa"),
                station.text("facilityNameJa"),
                station.text("facilityNameEn"),
                station.text("metadataStatus"),
                station.text("providerStationCode"),
                station.text("providerStationNetwork"),
                station.text("providerStationNameJa"),
                station.text("providerStationNameEn"),
                station["providerLatitude"] as? Number,
                station["providerLongitude"] as? Number,
                station.text("note"),
                station.text("automaticEnglishName")
            )

            val areaCode = station.text("areaCode")
                ?: error("Station $code has no JMA reporting-area code")
            val areaNameJa = station.text("areaNameJa")
                ?: error("Station $code has no JMA reporting-area name")
            val prefectureJa = station.text("prefectureJa")
                ?: error("Station $code has no prefecture")
            val areaNameEn = reportingAreaEnglishName(areaNameJa)
            require(!containsJapanese(areaNameEn)) {
                "JMA reporting-area English name still contains Japanese: $areaNameEn"
            }
            val areaRow = listOf(areaNameJa, areaNameEn, prefectureJa)
            require(reportingAreas[areaCode] in listOf(null, areaRow)) {
                "Conflicting JMA reporting-area metadata for $areaCode"
            }
            reportingAreas[areaCode] = areaRow

            val municipalityCode = station.text("municipalityCode")
                ?: error("Station $code has no municipality code")
            val municipalityRow = listOf(areaCode, prefectureJa)
            require(municipalityParents[municipalityCode] in listOf(null, municipalityRow)) {
                "Conflicting municipality parent metadata for $municipalityCode"
            }
            municipalityParents[municipalityCode] = municipalityRow
        }
        require(reportingAreas.size == 188) {
            "Expected 188 station-backed JMA reporting areas; found ${reportingAreas.size}"
        }
        require(municipalityParents.size == 1_894) {
            "Expected 1,894 municipality parents; found ${municipalityParents.size}"
        }

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            JsonOutput.toJson(
                linkedMapOf(
                    "version" to 2,
                    "sourceSchemaVersion" to schemaVersion,
                    "stations" to details,
                    "reportingAreas" to reportingAreas,
                    "municipalityParents" to municipalityParents
                )
            ) + "\n",
            Charsets.UTF_8
        )
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Single build-time boundary for every Sandbox capability. Sandbox source stays
// compiled, but all of its activation paths become inert when this is false.
val sandboxEnabled = true

val stationMetadataSource = rootProject.layout.projectDirectory.file(
    "outputs/station-name-audit/station_metadata_sources.json"
)
val jmaPlaceNamesSource = layout.projectDirectory.file("src/main/res/raw/jma_place_names.json")
val generatedStationDetailsResDir = layout.buildDirectory.dir(
    "generated/station-details/res"
)
val generateStationDetails = tasks.register<GenerateStationDetailsTask>(
    "generateStationDetails"
) {
    group = "build setup"
    description = "Generates compact APK station details from the audited station source."
    sourceFile.set(stationMetadataSource)
    placeNamesFile.set(jmaPlaceNamesSource)
    outputFile.set(generatedStationDetailsResDir.map { it.file("raw/station_details.json") })
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(::load)
    }
}
fun signingProperty(name: String): String =
    signingProperties.getProperty(name)
        ?: error("Missing signing property: $name")

android {
    namespace = "cz.misa.quakedeck"
    compileSdk = 36

    val sharedSigningConfig = if (signingPropertiesFile.isFile) {
        signingConfigs.create("shared") {
            storeFile = rootProject.file(signingProperty("storeFile"))
            storePassword = signingProperty("storePassword")
            keyAlias = signingProperty("keyAlias")
            keyPassword = signingProperty("keyPassword")
        }
    } else {
        null
    }

    defaultConfig {
        applicationId = "cz.misa.quakedeck"
        minSdk = 26
        targetSdk = 36
        versionCode = 221
        versionName = "0.10.1-dev.1"
        buildConfigField("boolean", "SANDBOX_ENABLED", sandboxEnabled.toString())
        buildConfigField(
            "String",
            "DMDSS_OAUTH_CLIENT_ID",
            "\"CId.q0U3EiI6XWzaerg1EhQsoo8n_nwF3c1mAGe49m7xX-md\""
        )
        buildConfigField(
            "String",
            "DMDSS_OAUTH_REDIRECT_URI",
            "\"cz.misa.quakedeck://oauth/dmdss\""
        )
    }

    buildTypes {
        getByName("debug") {
            sharedSigningConfig?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            sharedSigningConfig?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").res.directories.add(
        generatedStationDetailsResDir.get().asFile.absolutePath
    )
    bundle {
        language {
            // QuakeDeck has an in-app language picker, so every installed split
            // must contain English, Czech and Japanese resources.
            enableSplit = false
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateStationDetails)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}


dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
