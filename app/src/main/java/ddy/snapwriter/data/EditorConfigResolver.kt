package ddy.snapwriter.data

import android.content.Context
import android.net.Uri
import ddy.snapwriter.config.EditorConfig
import ddy.snapwriter.config.EditorDefaults

class EditorConfigResolver(context: Context) {
    private val preferences = FileLevelPreferences(context)

    /**
     * Resolves configuration for a file.
     * @param uri The document Uri (used for storage keys).
     * @param fileName The name of the file (used for determining default type).
     */
    fun resolve(uri: Uri, fileName: String): EditorConfig {
        // Use the name to get type-specific defaults
        val defaultConfig = EditorDefaults.getDefaultConfig(fileName)

        // Load custom overrides using the Uri key
        val saved = preferences.loadConfig(uri)

        return if (saved != null) {
            defaultConfig.copy(
                showLineNumbers = saved.showLineNumbers,
                wordWrapEnabled = saved.wordWrapEnabled,
                highlightCurrentLine = saved.highlightCurrentLine,
                useMonospaceFont = saved.useMonospaceFont,
                autoPairBraces = saved.autoPairBraces,
                fileExtension = saved.fileExtension
            )
        } else defaultConfig
    }

    fun persist(uri: Uri, config: EditorConfig) {
        preferences.saveConfig(uri, config)
    }

    fun clear(uri: Uri) {
        preferences.clearConfig(uri)
    }
}