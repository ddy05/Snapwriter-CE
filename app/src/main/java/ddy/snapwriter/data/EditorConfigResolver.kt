package ddy.snapwriter.data

import android.content.Context
import ddy.snapwriter.config.EditorConfig
import ddy.snapwriter.config.EditorDefaults

class EditorConfigResolver(context: Context)
{
    private val preferences = FileLevelPreferences(context)

    fun resolve(fileName: String): EditorConfig {
        val defaultConfig = EditorDefaults.getDefaultConfig(fileName)
        val saved = preferences.loadConfig(fileName)
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

    fun persist(fileName: String, config: EditorConfig) { preferences.saveConfig(fileName, config) }
}