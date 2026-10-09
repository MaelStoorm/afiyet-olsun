import java.util.Properties

plugins {
    id("com.android.application")
}

// Depo kökü (android/ klasörünün bir üstü): oyunun kendisi (index.html) ve admob.properties orada durur.
val repoRoot: File = rootDir.parentFile

// ---------- AdMob kimlikleri ----------
// Kökteki admob.properties dosyasından okunur (appId=, rewardedId=). Boş bırakılırsa Google'ın resmi TEST kimlikleri kullanılır.
val admobProps = Properties().apply {
    val f = repoRoot.resolve("admob.properties")
    if (f.isFile) f.reader(Charsets.UTF_8).use { load(it) }
}

fun admobId(key: String, testId: String, format: Regex): String {
    val value = admobProps.getProperty(key, "").trim()
    if (value.isEmpty()) return testId
    require(format.matches(value)) { "admob.properties: '$key' değeri geçersiz görünüyor: $value" }
    return value
}

val admobAppId = admobId("appId", "ca-app-pub-3940256099942544~3347511713", Regex("""ca-app-pub-\d+~\d+"""))
val admobRewardedId = admobId("rewardedId", "ca-app-pub-3940256099942544/5224354917", Regex("""ca-app-pub-\d+/\d+"""))
if (admobAppId.startsWith("ca-app-pub-3940256099942544") || admobRewardedId.startsWith("ca-app-pub-3940256099942544")) {
    logger.warn("UYARI: admob.properties boş, Google'ın TEST reklam kimlikleri kullanılıyor. Bu reklamlar gelir getirmez.")
}

// ---------- oyun dosyası ----------
/**
 * Kökteki index.html'i uygulamanın assets klasörüne koyar. Google Fonts bağlantıları kaldırılır,
 * yerine uygulamanın kendi içinde taşıdığı fonts.css bağlanır (yazı tipleri internetsiz de görünür).
 */
abstract class GameAssetsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val gameHtml: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copyGame() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val fontsLink = """<link rel="stylesheet" href="fonts.css">"""
        val cssLink = Regex("""<link\b[^>]*fonts\.googleapis\.com/css[^>]*>""")
        val otherFontLinks = Regex("""[ \t]*<link\b[^>]*fonts\.(googleapis|gstatic)\.com[^>]*>[ \t]*\r?\n?""")
        var html = gameHtml.get().asFile.readText(Charsets.UTF_8)
        html = html.replaceFirst(cssLink, fontsLink)
        html = otherFontLinks.replace(html, "")
        if (!html.contains(fontsLink)) {
            html = when {
                html.contains("</head>") -> html.replaceFirst("</head>", "$fontsLink\n</head>")
                html.contains("<style") -> html.replaceFirst("<style", "$fontsLink\n<style")
                else -> "$fontsLink\n$html"
            }
        }
        out.resolve("index.html").writeText(html, Charsets.UTF_8)
    }
}

android {
    namespace = "com.afiyetolsun.oyun"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.afiyetolsun.oyun"
        minSdk = 24
        targetSdk = 36
        versionCode = 18
        versionName = "1.5.1"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"$admobRewardedId\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // İmza ayarı yok: AAB imzasız çıkar, Play'e yüklemeden önce yükleme anahtarıyla imzalanır.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }
}

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercase() }
        val task = project.tasks.register<GameAssetsTask>("copyGameHtml$suffix") {
            gameHtml.set(repoRoot.resolve("index.html"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, GameAssetsTask::outputDir)
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
