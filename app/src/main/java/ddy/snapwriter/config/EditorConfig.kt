package ddy.snapwriter.config

data class EditorConfig(
    val showLineNumbers: Boolean,
    val wordWrapEnabled: Boolean,
    val highlightCurrentLine: Boolean,
    val useMonospaceFont: Boolean,
    val autoPairBraces: Boolean,
    val fileExtension: String = "php"
)