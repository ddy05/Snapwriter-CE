package ddy.snapwriter.config

import android.content.Context
import android.net.Uri
import ddy.snapwriter.config.FileLevelPreferences

class EditorConfigResolver(context: Context) {
    private val preferences = FileLevelPreferences(context)

    fun resolve(uri: Uri, fileName: String): EditorConfig {
        val defaultConfig = EditorDefaults.getDefaultConfig(fileName)
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