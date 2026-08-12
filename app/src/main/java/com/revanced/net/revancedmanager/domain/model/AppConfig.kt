package com.revanced.net.revancedmanager.domain.model

/**
 * Configuration model for app settings
 */
data class AppConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: Language = Language.ENGLISH,
    val compactMode: Boolean = true, // Default to compact mode enabled
    val debugModeEnabled: Boolean = false,
    val autoDeleteApkEnabled: Boolean = true, // Delete the downloaded APK after a successful install
    val autoUpdateCheckEnabled: Boolean = true, // Daily background update check + notification
    val showUpdatePromptEnabled: Boolean = true // "N updates available" popup after launch refresh
)

/**
 * Theme mode options
 */
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

/**
 * Supported languages with ISO codes and emoji flags
 */
enum class Language(
    val code: String, 
    val displayName: String, 
    val flagEmoji: String
) {
    ENGLISH("en", "English", "🇬🇧"),
    VIETNAMESE("vi", "Tiếng Việt", "🇻🇳"),
    CHINESE("zh", "中文", "🇨🇳"),
    HINDI("hi", "हिन्दी", "🇮🇳"),
    INDONESIAN("id", "Bahasa Indonesia", "🇮🇩"),
    PORTUGUESE_BR("pt", "Português (Brasil)", "🇧🇷"),
    TURKISH("tr", "Türkçe", "🇹🇷"),
    SPANISH_MX("es", "Español (México)", "🇲🇽"),
    KOREAN("ko", "한국어", "🇰🇷"),
    FRENCH("fr", "Français", "🇫🇷"),
    POLISH("pl", "Polski", "🇵🇱"),
    GERMAN("de", "Deutsch", "🇩🇪"),
    MALAY("ms", "Bahasa Melayu", "🇲🇾"),
    ITALIAN("it", "Italiano", "🇮🇹"),
    FILIPINO("tl", "Filipino", "🇵🇭"),
    BENGALI("bn", "বাংলা", "🇧🇩"),
    RUSSIAN("ru", "Русский", "🇷🇺"),
    ROMANIAN("ro", "Română", "🇷🇴"),
    SPANISH_PE("es-PE", "Español (Perú)", "🇵🇪"),
    SPANISH_ES("es-ES", "Español (España)", "🇪🇸"),
    ARABIC("ar", "العربية", "🇸🇦"),
    CZECH("cs", "Čeština", "🇨🇿");
}