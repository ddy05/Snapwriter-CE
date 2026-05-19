package ddy.snapwriter.data

import android.content.Context
import androidx.core.content.edit
import ddy.snapwriter.config.EditorConfig
import ddy.snapwriter.config.EditorDefaults

class FileLevelPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)

    fun saveConfig(fileName: String, config: EditorConfig) {
        preferences.edit {
            putBoolean("${fileName}_wrap", config.wordWrapEnabled)
            putBoolean("${fileName}_lines", config.showLineNumbers)
            putBoolean("${fileName}_linehighlight", config.highlightCurrentLine)
            putBoolean("${fileName}_monospace", config.useMonospaceFont)
            putBoolean("${fileName}_autopair", config.autoPairBraces)
            putString("${fileName}_ext", config.fileExtension) // Save extension explicitly
        }
    }

    fun loadConfig(fileName: String): EditorConfig? {
        if (!preferences.contains("${fileName}_wrap")) return null
        val typeDefaults = EditorDefaults.getDefaultConfig(fileName)
        return EditorConfig(
            showLineNumbers = preferences.getBoolean("${fileName}_lines", typeDefaults.showLineNumbers),
            wordWrapEnabled = preferences.getBoolean("${fileName}_wrap", typeDefaults.wordWrapEnabled),
            highlightCurrentLine = preferences.getBoolean("${fileName}_linehighlight", typeDefaults.highlightCurrentLine),
            useMonospaceFont = preferences.getBoolean("${fileName}_monospace", typeDefaults.useMonospaceFont),
            autoPairBraces = preferences.getBoolean("${fileName}_autopair", typeDefaults.autoPairBraces),
            fileExtension = preferences.getString("${fileName}_ext", typeDefaults.fileExtension) ?: typeDefaults.fileExtension // Recover extension string
        )
    }

    fun clearConfig(fileName: String) {
        preferences.edit {
            remove("${fileName}_wrap")
            remove("${fileName}_lines")
            remove("${fileName}_linehighlight")
            remove("${fileName}_monospace")
            remove("${fileName}_autopair")
            remove("${fileName}_ext")
        }
    }
}