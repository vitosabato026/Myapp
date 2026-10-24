import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.bollettinoallergie"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.bollettinoallergie"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    lint {
        disable += "Typos" // il controllo ortografico è in inglese, i testi sono in italiano
        warningsAsErrors = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.18.0")
}

// Il catalogo degli allergeni è condiviso con il pacchetto Python: lo copiamo negli asset
// (www/allergeni.json) a ogni build invece di duplicarlo.
abstract class CopiaCatalogo : DefaultTask() {
    @get:InputFile
    abstract val sorgente: RegularFileProperty

    @get:OutputDirectory
    abstract val destinazione: DirectoryProperty

    @TaskAction
    fun copia() {
        val www = destinazione.get().dir("www").asFile.apply { mkdirs() }
        sorgente.get().asFile.copyTo(File(www, "allergeni.json"), overwrite = true)
    }
}

val copiaCatalogo = tasks.register<CopiaCatalogo>("copiaCatalogo") {
    sorgente.set(rootProject.layout.projectDirectory.file("../bollettino_allergie/catalogo/allergeni.json"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copiaCatalogo, CopiaCatalogo::destinazione)
    }
}
