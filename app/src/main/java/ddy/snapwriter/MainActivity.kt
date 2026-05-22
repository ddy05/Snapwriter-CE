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
    private var currentFileName: String = "Untitled"

    private var lastSavedContent: String = ""
    private var isDirty: Boolean = false
        set(value) {
            field = value
            // Optional: Update title to show an asterisk when dirty
            tvCurrentFile.text = if (value) "$currentFileName*" else currentFileName
        }

    private val extendedLanguages = arrayOf("html", "php", "js", "css", "java", "cs", "kt", "py", "json", "sql", "txt", "md")

    private fun getFileNameFromUri(uri: Uri): String {
        return DocumentFile.fromSingleUri(this, uri)?.name ?: "Untitled"
    }

    private var exitDialog: AlertDialog? = null

    private fun toggleExitDialog() {
        if (exitDialog != null && exitDialog!!.isShowing) {
            exitDialog!!.dismiss()
            exitDialog = null
        } else {
            val builder = AlertDialog.Builder(this, R.style.NuclearDialogTheme)
            builder.setTitle("Are you sure you want to exit?")
            builder.setPositiveButton("Exit") { _, _ -> finish() }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                exitDialog = null
            }
            exitDialog = builder.create()
            exitDialog!!.show()
            exitDialog!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#FF5555"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Check if dialog is already showing (to avoid opening multiple)
                toggleExitDialog()
            }
        })

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
        val editorModeBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggleEditorMode)

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

        menuBtn.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "New File")
            popup.menu.add(0, 2, 1, "Open")
            popup.menu.add(0, 3, 2, "Save")
            popup.menu.add(0, 4, 3, "Save As")
            popup.menu.add(0, 5, 4, "Close")
            // Inside your popup.menu.add logic
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showNewFileDialog()
                    2 -> showOpenFileDialog()
                    3 -> saveActiveDocument()
                    4 -> showSaveAsDialog()
                    5 -> closeCurrentFile()
                }
                true
            }
            popup.show()
        }

        editorModeBtn.setOnClickListener {
            editor.isReadOnly = !editor.isReadOnly
            editorModeBtn.isChecked = editor.isReadOnly
            onSettingsChanged()

            val tooltip = if (editor.isReadOnly) "Currently Viewing" else "Currently Editing"
            androidx.appcompat.widget.TooltipCompat.setTooltipText(editorModeBtn, tooltip)
        }

        editor.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                isDirty = s.toString() != lastSavedContent
            }
        })
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

    private fun closeCurrentFile() {
        if (isDirty) {
            AlertDialog.Builder(this, R.style.NuclearDialogTheme)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Close anyway?")
                .setPositiveButton("Close") { _, _ -> resetEditorState() }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            resetEditorState()
        }
    }

    private fun resetEditorState() {
        currentUri = null
        currentFileName = "Untitled"
        editor.setText("")
        lastSavedContent = ""
        isDirty = false
        tvCurrentFile.text = currentFileName
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

        contentResolver.openInputStream(uri)?.use { inputStream ->
            val content = inputStream.bufferedReader().use { it.readText() }
            editor.setText(content)
            lastSavedContent = content
            isDirty = false
            loadAndApplyFileState(uri, currentFileName)
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun saveActiveDocument() {
        currentUri?.let { uri ->
            saveToUri(uri, editor.text.toString())
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "Select a location to save...", Toast.LENGTH_SHORT).show()
            showSaveAsDialog()
        }
    }

    private fun saveToUri(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri, "wt")?.use { it.writer().use { w -> w.write(text) } }
        lastSavedContent = text
        isDirty = false
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
            putExtra(Intent.EXTRA_TITLE, "")
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