package ddy.snapwriter

import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import ddy.snapwriter.config.EditorConfig
import ddy.snapwriter.ui.editor.CodeEditor
import ddy.snapwriter.data.EditorConfigResolver
import ddy.snapwriter.R

class MainActivity : AppCompatActivity()
{
    private lateinit var editor: CodeEditor
    private lateinit var resolver: EditorConfigResolver

    private var currentFile = "wow.html"

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<android.view.View>(R.id.rootLayout)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())

            val topPadding = systemBars.top
            val bottomPadding = if (insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())) {
                ime.bottom
            } else {
                systemBars.bottom
            }

            view.setPadding(systemBars.left, topPadding, systemBars.right, bottomPadding)
            insets
        }

        editor = findViewById(R.id.codeEditor)

        val wrapBtn = findViewById<Button>(R.id.btnWrap)
        val lineBtn = findViewById<Button>(R.id.btnLines)
        val readOnlyBtn = findViewById<Button>(R.id.btnReadOnly)

        resolver = EditorConfigResolver(this)

        val config = resolver.resolve(currentFile)
        editor.applyConfig(config)

        findViewById<Button>(R.id.btnIndent).setOnClickListener { editor.indentSelectedText() }
        findViewById<Button>(R.id.btnDeindent).setOnClickListener { editor.deindentSelectedText() }
        findViewById<Button>(R.id.btnCurly).setOnClickListener { editor.insertSymbolPair("{", "}") }
        findViewById<Button>(R.id.btnSquare).setOnClickListener { editor.insertSymbolPair("[", "]") }
        findViewById<Button>(R.id.btnParens).setOnClickListener { editor.insertSymbolPair("(", ")") }
        findViewById<Button>(R.id.btnCommandPalette).setOnClickListener { openCommandPalette() }

        wrapBtn.setOnClickListener {
            editor.wordWrapEnabled = !editor.wordWrapEnabled
            saveCurrentConfig()
        }

        lineBtn.setOnClickListener {
            editor.showLineNumbers = !editor.showLineNumbers
            saveCurrentConfig()
        }

        readOnlyBtn.setOnClickListener {
            editor.isReadOnly = !editor.isReadOnly
            if (editor.isReadOnly) {
                readOnlyBtn.text = "Edit Mode"
                findViewById<android.view.View>(R.id.editorToolbar).visibility = android.view.View.GONE

                editor.clearFocus()
            } else {
                readOnlyBtn.text = "View Mode"
                findViewById<android.view.View>(R.id.editorToolbar).visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun openCommandPalette()
    {
        android.widget.Toast.makeText(this, "Opening Command Palette...", android.widget.Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun saveCurrentConfig()
    {
        val currentlyMonospace = editor.typeface == android.graphics.Typeface.MONOSPACE

        resolver.persist(
            currentFile,
            EditorConfig(
                showLineNumbers = editor.showLineNumbers,
                wordWrapEnabled = editor.wordWrapEnabled,
                highlightCurrentLine = editor.highlightCurrentLine,
                useMonospaceFont = currentlyMonospace,
                autoPairBraces = editor.autoPairBraces,
                fileExtension = currentFile.substringAfterLast('.', "php")
            )
        )
    }
}