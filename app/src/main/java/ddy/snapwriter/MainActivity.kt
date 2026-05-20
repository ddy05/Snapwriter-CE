package ddy.snapwriter

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ddy.snapwriter.config.EditorConfig
import ddy.snapwriter.ui.editor.CodeEditor
import ddy.snapwriter.data.EditorConfigResolver
import java.io.File

class MainActivity : AppCompatActivity()
{
    private lateinit var editor: CodeEditor
    private lateinit var resolver: EditorConfigResolver
    private lateinit var tvCurrentFile: TextView

    private var currentFile = "wow.html"

    private val extendedLanguages = arrayOf("html", "php", "js", "css", "java", "cs", "kt", "py", "json", "sql", "txt", "md")

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?)
    {
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
        tvCurrentFile.text = currentFile

        val wrapBtn = findViewById<Button>(R.id.btnWrap)
        val lineBtn = findViewById<Button>(R.id.btnLines)
        val readOnlyBtn = findViewById<Button>(R.id.btnReadOnly)

        resolver = EditorConfigResolver(this)
        loadAndApplyFileState(currentFile)

        findViewById<Button>(R.id.btnIndent).setOnClickListener { editor.indentSelectedText() }
        findViewById<Button>(R.id.btnDeindent).setOnClickListener { editor.deindentSelectedText() }
        findViewById<Button>(R.id.btnCurly).setOnClickListener { editor.insertSymbolPair("{", "}") }
        findViewById<Button>(R.id.btnSquare).setOnClickListener { editor.insertSymbolPair("[", "]") }
        findViewById<Button>(R.id.btnParens).setOnClickListener { editor.insertSymbolPair("(", ")") }
        findViewById<Button>(R.id.btnCommandPalette).setOnClickListener { openCommandPalette() }

//        findViewById<Button>(R.id.btnNewFile).setOnClickListener { showNewFileDialog() }
//        findViewById<Button>(R.id.btnOpenFile).setOnClickListener { showOpenFileDialog() }
//        findViewById<Button>(R.id.btnSaveFile).setOnClickListener { saveActiveDocument() }
//        findViewById<Button>(R.id.btnSaveAsFile).setOnClickListener { showSaveAsDialog() }

        // Inside onCreate, find and enable your button
        val menuBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.menuBtn)
        menuBtn.isEnabled = true // Ensure this is true!

        menuBtn.setOnClickListener { view ->
            menuBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#7075FF")))

            val popup = android.widget.PopupMenu(this, view)

            // Add menu items (ID, Order, Category, Title)
            popup.menu.add(0, 1, 0, "New File")
            popup.menu.add(0, 2, 1, "Open")
            popup.menu.add(0, 3, 2, "Save")
            popup.menu.add(0, 4, 3, "Save As")
            popup.menu.add(0, 5, 4, "Close")

            // Handle clicks
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showNewFileDialog()
                    2 -> showOpenFileDialog()
                    3 -> saveActiveDocument()
                    4 -> showSaveAsDialog()
                    5 -> {
                        editor.setText("")
                        Toast.makeText(this, "Document closed", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }

            // 2. Set the dismiss listener to revert the color
            popup.setOnDismissListener {
                // Revert to the default color #505063
                menuBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#505063")))
            }

            popup.show()
        }

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
                findViewById<View>(R.id.editorToolbar).visibility = View.GONE
                editor.clearFocus()
            } else {
                readOnlyBtn.text = "View Mode"
                findViewById<View>(R.id.editorToolbar).visibility = View.VISIBLE
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showNewFileDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Create New File")

        val view = LayoutInflater.from(this).inflate(android.R.layout.activity_list_item, null)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(45, 20, 45, 20)
        }

        val inputName = EditText(this).apply {
            hint = "Filename (e.g. index)"
            setSingleLine(true)
        }
        val spinnerExt = Spinner(this)
        spinnerExt.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, extendedLanguages)

        container.addView(inputName)
        container.addView(spinnerExt)
        builder.setView(container)

        builder.setPositiveButton("Create") { dialog, _ ->
            val baseName = inputName.text.toString().trim()
            val selectedExt = spinnerExt.selectedItem.toString()

            if (baseName.isEmpty()) {
                Toast.makeText(this, "Filename cannot be empty", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val targetFullName = "$baseName.$selectedExt"
            val file = File(filesDir, targetFullName)

            try {
                file.writeText("")
                currentFile = targetFullName
                tvCurrentFile.text = currentFile
                editor.setText("")
                loadAndApplyFileState(currentFile)
                Toast.makeText(this, "Created: $targetFullName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error initializing file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showOpenFileDialog() {
        val filesList = filesDir.listFiles()?.map { it.name }?.toTypedArray() ?: emptyArray()

        if (filesList.isEmpty()) {
            Toast.makeText(this, "No saved files found", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Open File")
        builder.setItems(filesList) { dialog, which ->
            val selectedFileName = filesList[which]
            val file = File(filesDir, selectedFileName)

            if (file.exists()) {
                currentFile = selectedFileName
                tvCurrentFile.text = currentFile

                val fileContent = file.readText()
                editor.setText(fileContent)

                loadAndApplyFileState(currentFile)
                Toast.makeText(this, "Loaded: $selectedFileName", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun saveActiveDocument() {
        val file = File(filesDir, currentFile)
        try {
            file.writeText(editor.text.toString())
            Toast.makeText(this, "Saved successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showSaveAsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Save As")

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(45, 20, 45, 20)
        }

        val inputName = EditText(this).apply {
            hint = "New filename"
            setText(currentFile.substringBeforeLast('.'))
            setSingleLine(true)
        }
        val spinnerExt = Spinner(this)
        spinnerExt.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, extendedLanguages)

        val activeExtIdx = extendedLanguages.indexOf(currentFile.substringAfterLast('.', "html"))
        if (activeExtIdx != -1) spinnerExt.setSelection(activeExtIdx)

        container.addView(inputName)
        container.addView(spinnerExt)
        builder.setView(container)

        builder.setPositiveButton("Save") { dialog, _ ->
            val baseName = inputName.text.toString().trim()
            val selectedExt = spinnerExt.selectedItem.toString()

            if (baseName.isEmpty()) {
                Toast.makeText(this, "Filename cannot be empty", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val targetFullName = "$baseName.$selectedExt"
            val file = File(filesDir, targetFullName)

            try {
                file.writeText(editor.text.toString())
                currentFile = targetFullName
                tvCurrentFile.text = currentFile
                loadAndApplyFileState(currentFile)
                Toast.makeText(this, "Saved as: $targetFullName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error cloning workspace: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun loadAndApplyFileState(fileName: String) {
        val config = resolver.resolve(fileName)
        editor.applyConfig(config)
    }

    private fun openCommandPalette()
    {
        Toast.makeText(this, "Opening Command Palette...", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun saveCurrentConfig() {
        // We don't need to check typeface here anymore,
        // the EditorConfigResolver handles it based on file extension
        resolver.persist(
            currentFile,
            EditorConfig(
                showLineNumbers = editor.showLineNumbers,
                wordWrapEnabled = editor.wordWrapEnabled,
                highlightCurrentLine = editor.highlightCurrentLine,
                useMonospaceFont = (currentFile.substringAfterLast('.', "") !in listOf("txt", "md")),
                autoPairBraces = editor.autoPairBraces,
                fileExtension = currentFile.substringAfterLast('.', "php")
            )
        )
    }
}