package es.upm.sensoasis.domain.model

/**
 * Supported UI and Text-to-Speech languages: English, Spanish (Castilian), and Catalan.
 */
enum class AppLanguage(val code: String, val shortLabel: String) {
    EN("en", "EN"),
    ES("es", "ES"),
    CA("ca", "CA");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
    }
}
