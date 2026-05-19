package ddy.snapwriter.config

object EditorDefaults {
    private val CODE_EXTENSIONS = setOf(
        "cs", "java", "kt", "py",
        "html", "css", "js", "php",
        "json"
    )
    private val TEXT_EXTENSIONS = setOf(
        "txt", "md"
    )

    fun getDefaultConfig(fileName: String): EditorConfig {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in CODE_EXTENSIONS -> EditorConfig(
                showLineNumbers = true,
                wordWrapEnabled = false,
                highlightCurrentLine = true,
                useMonospaceFont = true,
                autoPairBraces = true,
                fileExtension = ext
            )
            in TEXT_EXTENSIONS -> EditorConfig(
                showLineNumbers = false,
                wordWrapEnabled = true,
                highlightCurrentLine = false,
                useMonospaceFont = false,
                autoPairBraces = false,
                fileExtension = ext
            )
            else -> EditorConfig(
                showLineNumbers = true,
                wordWrapEnabled = true,
                highlightCurrentLine = false,
                useMonospaceFont = true,
                autoPairBraces = true,
                fileExtension = ext
            )
        }
    }
}