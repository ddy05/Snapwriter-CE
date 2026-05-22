package ddy.snapwriter.config

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class FileLevelPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)

    fun saveConfig(uri: Uri, config: EditorConfig) {
        val uriKey = uri.toString()
        preferences.edit {
            putString("${uriKey}_ext", config.fileExtension)
            putBoolean("${uriKey}_wrap", config.wordWrapEnabled)
            putBoolean("${uriKey}_lines", config.showLineNumbers)
            putBoolean("${uriKey}_linehighlight", config.highlightCurrentLine)
            putBoolean("${uriKey}_monospace", config.useMonospaceFont)
            putBoolean("${uriKey}_autopair", config.autoPairBraces)
        }
    }

    fun loadConfig(uri: Uri): EditorConfig? {
        val uriKey = uri.toString()

        if (!preferences.contains("${uriKey}_wrap")) return null

        val savedExt = preferences.getString("${uriKey}_ext", "php") ?: "php"
        val typeDefaults = EditorDefaults.getDefaultConfig(savedExt)

        return EditorConfig(
            showLineNumbers = preferences.getBoolean("${uriKey}_lines", typeDefaults.showLineNumbers),
            wordWrapEnabled = preferences.getBoolean("${uriKey}_wrap", typeDefaults.wordWrapEnabled),
            highlightCurrentLine = preferences.getBoolean("${uriKey}_linehighlight", typeDefaults.highlightCurrentLine),
            useMonospaceFont = preferences.getBoolean("${uriKey}_monospace", typeDefaults.useMonospaceFont),
            autoPairBraces = preferences.getBoolean("${uriKey}_autopair", typeDefaults.autoPairBraces),
            fileExtension = savedExt
        )
    }

    fun clearConfig(uri: Uri) {
        val uriKey = uri.toString()
        preferences.edit {
            remove("${uriKey}_wrap")
            remove("${uriKey}_lines")
            remove("${uriKey}_linehighlight")
            remove("${uriKey}_monospace")
            remove("${uriKey}_autopair")
            remove("${uriKey}_ext")
        }
    }
}