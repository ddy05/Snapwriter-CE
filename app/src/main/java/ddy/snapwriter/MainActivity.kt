package ddy.snapwriter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import ddy.snapwriter.config.EditorConfig
import ddy.snapwriter.ui.editor.CodeEditor
import ddy.snapwriter.data.EditorConfigResolver

class MainActivity : AppCompatActivity() {
    private lateinit var editor: CodeEditor
    private lateinit var resolver: EditorConfigResolver
    private lateinit var tvCurrentFile: TextView

    private var currentUri: Uri? = null
    private var currentFileName: String = "untitled.txt"

    private val extendedLanguages = arrayOf("html", "php", "js", "css", "java", "cs", "kt", "py", "json", "sql", "txt", "md")

    private fun getFileNameFromUri(uri: Uri): String {
        return DocumentFile.fromSingleUri(this, uri)?.name ?: "untitled.txt"
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<View>(R.id.rootLayout)
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
        tvCurrentFile = findViewById(R.id.tvCurrentFile)
        tvCurrentFile.text = currentFileName

        resolver = EditorConfigResolver(this)

        val wrapBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWrap)
        val lineBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLines)
        val menuBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.menuBtn)
        val readOnlyBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReadOnly)

        menuBtn.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "New File")
            popup.menu.add(0, 2, 1, "Open")
            popup.menu.add(0, 3, 2, "Save")
            // Inside your popup.menu.add logic
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showNewFileDialog()
                    2 -> showOpenFileDialog()
                    3 -> saveActiveDocument()
                    4 -> showSaveAsDialog() // Add this option to your menu!
                }
                true
            }
            popup.show()
        }

        wrapBtn.setOnClickListener {
            editor.wordWrapEnabled = !editor.wordWrapEnabled
            wrapBtn.isChecked = editor.wordWrapEnabled
            onSettingsChanged()
        }

        lineBtn.setOnClickListener {
            editor.showLineNumbers = !editor.showLineNumbers
            lineBtn.isChecked = editor.showLineNumbers
            onSettingsChanged()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                loadFromUri(uri)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Grant persistable permissions
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                // For "New File", write empty content; for "Save As", write current editor text
                if (isPendingSaveAs) {
                    saveToUri(uri, editor.text.toString())
                    isPendingSaveAs = false
                } else {
                    saveToUri(uri, "")
                }

                // Load the newly created file
                loadFromUri(uri)
            }
        }
    }
    private var isPendingSaveAs = false // Flag to track intent purpose

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showOpenFileDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        openFileLauncher.launch(intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun loadFromUri(uri: Uri) {
        currentUri = uri
        currentFileName = getFileNameFromUri(uri)
        tvCurrentFile.text = currentFileName

        contentResolver.openInputStream(uri)?.use { inputStream ->
            val content = inputStream.bufferedReader().use { it.readText() }
            editor.setText(content)
            loadAndApplyFileState(uri, currentFileName)
        }
    }

    private fun saveActiveDocument() {
        currentUri?.let { uri ->
            saveToUri(uri, editor.text.toString())
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(this, "No file open", Toast.LENGTH_SHORT).show()
    }

    private fun saveToUri(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri, "wt")?.use { it.writer().use { w -> w.write(text) } }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun loadAndApplyFileState(uri: Uri, fileName: String) {
        editor.applyConfig(resolver.resolve(uri, fileName))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onSettingsChanged() {
        currentUri?.let { uri -> saveCurrentConfig(uri) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun saveCurrentConfig(uri: Uri) {
        resolver.persist(
            uri,
            EditorConfig(
                showLineNumbers = editor.showLineNumbers,
                wordWrapEnabled = editor.wordWrapEnabled,
                highlightCurrentLine = editor.highlightCurrentLine,
                useMonospaceFont = (currentFileName.substringAfterLast('.', "") !in listOf("txt", "md")),
                autoPairBraces = editor.autoPairBraces,
                fileExtension = currentFileName.substringAfterLast('.', "php")
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showNewFileDialog() {
        isPendingSaveAs = false
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, "untitled.txt")
        }
        createFileLauncher.launch(intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showSaveAsDialog() {
        isPendingSaveAs = true
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // Allow all file types
            putExtra(Intent.EXTRA_TITLE, currentFileName)
        }
        createFileLauncher.launch(intent)
    }
    private fun openCommandPalette() { Toast.makeText(this, "Palette...", Toast.LENGTH_SHORT).show() }
}