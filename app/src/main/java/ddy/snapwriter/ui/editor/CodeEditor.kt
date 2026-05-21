package ddy.snapwriter.ui.editor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Editable
import android.text.Layout
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import ddy.snapwriter.config.EditorConfig
import androidx.core.graphics.withTranslation
import androidx.core.graphics.toColorInt
import ddy.snapwriter.R
import java.util.regex.Pattern

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class CodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private var matchedOpenIndex: Int = -1
    private var matchedCloseIndex: Int = -1
    private var currentFileExtension: String = "php"

    private val clrKeyword = "#FF79C6".toColorInt()
    private val clrTag = "#FF5555".toColorInt()
    private val clrAttr = "#50FA7B".toColorInt()
    private val clrString = "#F1FA8C".toColorInt()
    private val clrComment = "#6272A4".toColorInt()
    private val clrVariable = "#BD93F9".toColorInt()
    private val clrLiteral = "#FFB86C".toColorInt()
    private val clrIdentifier = "#8BE9FD".toColorInt()

    private val gutterBorderPaint = Paint().apply {
        color = Color.parseColor("#333333") // Adjust color to your preference
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
        style = Paint.Style.STROKE
    }
    private val pairBackgroundPaint = Paint().apply {
        color = "#3300E6FF".toColorInt()
        style = Paint.Style.FILL
    }
    private val pairBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FF00E6FF".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
    }

    var showLineNumbers: Boolean = true
        set(value) { field = value; updatePaddingAndLayout(); invalidate() }
    var wordWrapEnabled: Boolean = true
        set(value) { field = value; applyWordWrap() }
    var highlightCurrentLine: Boolean = true
        set(value) { field = value; invalidate() }
    var autoPairBraces: Boolean = true

    var isReadOnly: Boolean = false
        set(value) {
            field = value
            if (value) {
                showSoftInputOnFocus = false
                clearFocus()
                isFocusable = true
                isFocusableInTouchMode = true
                isCursorVisible = false
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(windowToken, 0)
            } else {
                showSoftInputOnFocus = true
                isFocusable = true
                isFocusableInTouchMode = true
                isCursorVisible = true
            }
            invalidate()
        }

    private val gutterWidth = 100
    private val internalTextPadding = 20

    private val totalLeftOffset: Int
        get() = if (showLineNumbers) gutterWidth + internalTextPadding else internalTextPadding

    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textAlign = Paint.Align.RIGHT
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        isSubpixelText = true
    }
    private val currentLinePaint = Paint().apply { color = "#308084ff".toColorInt() }
    private val gutterBackgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        textSize = this@CodeEditor.textSize
        isSubpixelText = true
    }

    private var isScrolling = false
    private var rollbackAnimator: ValueAnimator? = null
    private var isSelfModifyingText = false

    private val jsKeywords = Pattern.compile("\\b(break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|function|if|import|in|instanceof|new|return|super|switch|this|throw|try|typeof|var|void|while|with|yield|let|await)\\b")
    private val phpKeywords = Pattern.compile("\\b(abstract|and|array|as|break|case|catch|class|clone|const|continue|declare|default|die|do|echo|else|elseif|empty|enddeclare|endfor|endforeach|endif|endswitch|endwhile|eval|exit|extends|final|finally|for|foreach|function|global|if|implements|include|include_once|instanceof|insteadof|interface|isset|list|namespace|new|or|print|private|protected|public|require|require_once|return|session_destroy|session_start|session_unset|static|switch|throw|trait|try|unset|use|var|while|xor|yield|fn|match)\\b")
    private val cssKeywords = Pattern.compile("\\b(color|background|margin|padding|border|display|position|top|left|right|bottom|width|height|font|text|flex|grid|align|justify|opacity|visibility|z-index|transform|transition|animation)\\b")

    private val javaKeywords = Pattern.compile("\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|var|record|yield)\\b")
    private val csharpKeywords = Pattern.compile("\\b(abstract|as|base|bool|break|byte|case|catch|char|checked|class|const|continue|decimal|default|delegate|do|double|else|enum|event|explicit|extern|false|finally|fixed|float|for|foreach|goto|if|implicit|in|int|interface|internal|is|lock|long|namespace|new|null|object|operator|out|override|params|private|protected|public|readonly|ref|return|sbyte|sealed|short|sizeof|stackalloc|static|string|struct|switch|this|throw|true|true|try|typeof|uint|ulong|unchecked|unsafe|ushort|using|virtual|void|volatile|while|add|alias|ascending|async|await|by|descending|dynamic|from|get|global|group|into|join|let|nameof|on|orderby|partial|remove|select|set|value|var|when|where|yield)\\b")
    private val kotlinKeywords = Pattern.compile("\\b(as|as\\?|break|class|continue|do|else|false|for|fun|if|in|!in|interface|is|!is|null|object|package|return|super|this|throw|true|try|typealias|typeof|val|var|when|while|by|constructor|delegate|dynamic|field|file|init|param|property|receiver|setparam|get|set|data|enum|open|abstract|internal|private|protected|public|sealed|vararg|inline|noinline|crossinline|external|out|in|reified|companion|expect|actual|suspend)\\b")
    private val pythonKeywords = Pattern.compile("\\b(False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield)\\b")

    private val sqlKeywords = Pattern.compile("(?i)\\b(SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|JOIN|LEFT|RIGHT|INNER|OUTER|ON|GROUP|BY|ORDER|HAVING|LIMIT|UNION|ALL|CREATE|TABLE|DROP|ALTER|INDEX|PRIMARY|KEY|FOREIGN|REFERENCES|NOT|NULL|DEFAULT|UNIQUE|CONSTRAINT|DATABASE|USE|AS|AND|OR|IN|LIKE|BETWEEN|IS|EXISTS|ANY|ALL|CASE|WHEN|THEN|ELSE|END|JOIN)\\b")

    private val genericLiterals = Pattern.compile("\\b(true|false|null|NaN|undefined|TRUE|FALSE|NULL)\\b|\\b\\d+(\\.\\d+)?\\b")

    private val htmlVoidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")

    private fun scanAndFindMatchingPairs() {
        matchedOpenIndex = -1
        matchedCloseIndex = -1

        val content = text ?: return
        val start = selectionStart
        val end = selectionEnd

        if (start != end || start < 0) return

        var cursorTargetOffset = start - 1
        var targetChar = if (cursorTargetOffset >= 0 && cursorTargetOffset < content.length) content[cursorTargetOffset] else null

        if (targetChar != '{' && targetChar != '}' && targetChar != '(' && targetChar != ')' && targetChar != '[' && targetChar != ']') {
            cursorTargetOffset = start
            targetChar = if (cursorTargetOffset >= 0 && cursorTargetOffset < content.length) content[cursorTargetOffset] else null
        }

        if (targetChar == null) return

        when (targetChar) {
            '{', '(', '[' -> {
                val closeChar = when (targetChar) {
                    '{' -> '}'
                    '(' -> ')'
                    else -> ']'
                }
                var depth = 1
                var scanIdx = cursorTargetOffset + 1
                while (scanIdx < content.length) {
                    if (content[scanIdx] == targetChar) depth++
                    else if (content[scanIdx] == closeChar) depth--

                    if (depth == 0) {
                        matchedOpenIndex = cursorTargetOffset
                        matchedCloseIndex = scanIdx
                        break
                    }
                    scanIdx++
                }
            }
            '}', ')', ']' -> {
                val openChar = when (targetChar) {
                    '}' -> '{'
                    ')' -> '('
                    else -> '['
                }
                var depth = 1
                var scanIdx = cursorTargetOffset - 1
                while (scanIdx >= 0) {
                    if (content[scanIdx] == targetChar) depth++
                    else if (content[scanIdx] == openChar) depth--

                    if (depth == 0) {
                        matchedOpenIndex = scanIdx
                        matchedCloseIndex = cursorTargetOffset
                        break
                    }
                    scanIdx--
                }
            }
        }
    }

    private fun calculateMaxTextWidth(): Int {
        val rawText = text?.toString() ?: ""
        var maxLineWidth = 0f
        if (rawText.isNotEmpty()) {
            val lines = rawText.split("\n")
            for (line in lines) {
                val cleanLine = line.replace("\t", " ")
                val lineWidth = paint.measureText(cleanLine)
                if (lineWidth > maxLineWidth) {
                    maxLineWidth = lineWidth
                }
            }
        }
        return maxLineWidth.toInt() + totalLeftOffset + paddingRight + 300
    }

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (wordWrapEnabled) return false
                val layout = layout ?: return false
                isScrolling = true
                rollbackAnimator?.cancel()

                val maxScrollX = (calculateMaxTextWidth() - width).coerceAtLeast(0)
                val maxScrollY = (layout.height - height + paddingTop + paddingBottom).coerceAtLeast(0)

                val nextX = scrollX + distanceX.toInt()
                val nextY = scrollY + distanceY.toInt()

                val deltaX = when {
                    nextX < 0 -> -scrollX
                    nextX > maxScrollX -> maxScrollX - scrollX
                    else -> distanceX.toInt()
                }
                val deltaY = when {
                    nextY < 0 -> -scrollY
                    nextY > maxScrollY -> maxScrollY - scrollY
                    else -> distanceY.toInt()
                }

                scrollBy(deltaX, deltaY)
                return true
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean { return false }
        })

    private var sansTypeface: android.graphics.Typeface? = null
    private var monoTypeface: android.graphics.Typeface? = null

//    private fun applyTypefaceForExtension(ext: String) {
//        val isTextual = (ext == "txt" || ext == "md")
//        this.typeface = if (isTextual) sansTypeface else monoTypeface
//        invalidate()
//    }

    // Inside your CodeEditor.kt, update how you set the paint
    private fun applyTypefaceForExtension(ext: String) {
        val isTextual = (ext == "txt" || ext == "md")
        val selectedTypeface = if (isTextual) sansTypeface else monoTypeface

        this.typeface = selectedTypeface

        // FORCE the paint to use weight 400
        this.paint.typeface = android.graphics.Typeface.create(selectedTypeface, android.graphics.Typeface.NORMAL)
        // Sometimes you need to force the paint weight for variable fonts
        this.paint.strokeWidth = 0.5f // Tiny adjustment if it still looks too light

        invalidate()
    }

    init {
        try {
            // Use ResourcesCompat to load from your res/font folder
            sansTypeface = androidx.core.content.res.ResourcesCompat.getFont(
                context,
                R.font.font_instrument_sans // Matches your res/font/font_instrument_sans.xml
            )
            monoTypeface = androidx.core.content.res.ResourcesCompat.getFont(
                context,
                R.font.font_suse_mono // Matches your res/font/font_suse_mono.xml
            )
        } catch (e: Exception) {
            // Fallback
            sansTypeface = android.graphics.Typeface.SANS_SERIF
            monoTypeface = android.graphics.Typeface.MONOSPACE
        }

        // Set your typeface
        lineNumberPaint.typeface = monoTypeface
        applyTypefaceForExtension(currentFileExtension)

        setBackgroundColor(Color.BLACK)
        setTextColor(Color.WHITE)

        inputType = android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_CLASS_TEXT
        isSingleLine = false
        overScrollMode = OVER_SCROLL_ALWAYS

        applyWordWrap()
        updatePaddingAndLayout()

        placeholderPaint.typeface = this.typeface

        addTextChangedListener(object : TextWatcher {
            private var lastInsertedChar: Char? = null
            private var lastInsertOffset: Int = -1

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isSelfModifyingText || count != 1) {
                    lastInsertedChar = null
                    return
                }
                val added = s?.subSequence(start, start + count)?.toString()
                if (!added.isNullOrEmpty()) {
                    lastInsertedChar = added[0]
                    lastInsertOffset = start
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (isSelfModifyingText || s == null) {
                    invalidate()
                    return
                }

                applyTabReplacementSpans(s)

                val typedChar = lastInsertedChar ?: run {
                    runSyntaxHighlighter(s)
                    return
                }

                var closingPair: String? = when (typedChar) {
                    '{' -> "}"
                    '(' -> ")"
                    '[' -> "]"
                    '"' -> "\""
                    '\'' -> if (currentFileExtension != "md" && currentFileExtension != "txt") "'" else null
                    else -> null
                }

                if (closingPair == null && typedChar == '?' && currentFileExtension == "php") {
                    if (lastInsertOffset > 0 && s[lastInsertOffset - 1] == '<') {
                        closingPair = "?>"
                    }
                }

                if (closingPair == null && typedChar == '>' && (currentFileExtension == "html" || currentFileExtension == "php")) {
                    val lookbackIdx = lastInsertOffset - 1
                    var openBracketIdx = -1

                    for (k in lookbackIdx downTo 0) {
                        val charAtPos = s[k]
                        if (charAtPos == '<') {
                            openBracketIdx = k
                            break
                        }

                        if (charAtPos == '>' || charAtPos == '=' || charAtPos == ';') {
                            break
                        }
                    }

                    if (openBracketIdx != -1) {
                        val fullTagContent = s.substring(openBracketIdx + 1, lastInsertOffset).trim()

                        if (fullTagContent.isNotEmpty() &&
                            !fullTagContent.startsWith("/") &&
                            !fullTagContent.endsWith("/") &&
                            !fullTagContent.startsWith("!")
                        ) {
                            val tagName = fullTagContent.split(Pattern.compile("\\s+"))[0]

                            if (!htmlVoidTags.contains(tagName.lowercase())) {
                                closingPair = "</$tagName>"
                            }
                        }
                    }
                }

                if (closingPair != null && autoPairBraces) {
                    isSelfModifyingText = true
                    s.insert(lastInsertOffset + 1, closingPair)
                    setSelection(lastInsertOffset + 1)
                    isSelfModifyingText = false
                } else if (autoPairBraces) {
                    val isStandardClosingChar = typedChar == '}' || typedChar == ')' || typedChar == ']' || typedChar == '"' || typedChar == '\''

                    if (isStandardClosingChar) {
                        if (lastInsertOffset + 1 < s.length && s[lastInsertOffset + 1] == typedChar) {
                            isSelfModifyingText = true
                            s.delete(lastInsertOffset, lastInsertOffset + 1)
                            setSelection(lastInsertOffset + 1)
                            isSelfModifyingText = false
                        }
                    }
                    else if (typedChar == '>' && currentFileExtension == "php") {
                        if (lastInsertOffset > 0 && s[lastInsertOffset - 1] == '?') {
                            if (lastInsertOffset + 1 < s.length && s.startsWith("?>", lastInsertOffset - 1)) {
                                isSelfModifyingText = true
                                s.delete(lastInsertOffset - 1, lastInsertOffset + 1)
                                setSelection(lastInsertOffset + 1)
                                isSelfModifyingText = false
                            }
                        }
                    }
                }

                runSyntaxHighlighter(s)
                lastInsertedChar = null
                invalidate()
            }
        })
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (isReadOnly) return null
        val target = super.onCreateInputConnection(outAttrs) ?: return null

        return object : InputConnectionWrapper(target, true) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                    if (handleEnterKeyProcessing()) return true
                }
                return super.sendKeyEvent(event)
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text == "\n") {
                    if (handleEnterKeyProcessing()) return true
                }
                return super.commitText(text, newCursorPosition)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            if (event.isShiftPressed) {
                deindentSelectedText()
            } else {
                indentSelectedText()
            }
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
            if (handleEnterKeyProcessing()) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleEnterKeyProcessing(): Boolean {
        val start = selectionStart
        val end = selectionEnd
        val content = text

        if (start != end || start < 0 || content == null) return false

        val currentLayout = layout ?: return false
        val currentLine = currentLayout.getLineForOffset(start)
        val lineStart = currentLayout.getLineStart(currentLine)
        val lineEnd = currentLayout.getLineEnd(currentLine)

        val currentLineText = content.subSequence(lineStart, start).toString()
        val leadingWhitespace = currentLineText.takeWhile { it == ' ' || it == '\t' }

        if (currentFileExtension == "html" || currentFileExtension == "php") {
            val cleanLineCheck = currentLineText.trim()
            if (cleanLineCheck.equals("<!DOCTYPE html>", ignoreCase = true)) {
                val skeleton = "\n<html>\n\t<head>\n\t\t<title></title>\n\t\t<meta charset=\"UTF-8\">\n\t\t<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n\t</head>\n\t<body>\n\t\t\n\t</body>\n</html>"

                isSelfModifyingText = true
                content.replace(start, start, skeleton)
                applyTabReplacementSpans(content)
                runSyntaxHighlighter(content)
                isSelfModifyingText = false

                val targetCursorPos = start + "\n<html>\n\t<head>\n\t\t<title>".length
                setSelection(targetCursorPos)
                return true
            }
        }

        val leftSnippet = content.subSequence(lineStart, start).toString()
        val rightSnippet = content.subSequence(start, lineEnd).toString()

        var isPhpTagPair = false
        if (currentFileExtension == "php") {
            val rightTrimmed = rightSnippet.takeWhile { it == ' ' || it == '\t' }
            val hasCloseTagAhead = rightSnippet.startsWith("?>", rightTrimmed.length)
            if (hasCloseTagAhead) {
                val leftStripped = leftSnippet.dropLastWhile { it == ' ' || it == '\t' }
                isPhpTagPair = leftStripped.endsWith("<?php") ||
                        leftStripped.endsWith("<?") ||
                        leftStripped.endsWith("<?=")
            }
        }

        var isHtmlTagPair = false
        if (currentFileExtension == "html" || currentFileExtension == "php") {
            val leftStripped = leftSnippet.trimEnd()
            val rightStripped = rightSnippet.trimStart()

            if (leftStripped.endsWith(">") && rightStripped.startsWith("</")) {
                val openTagMatch = Pattern.compile("<([a-zA-Z1-6\\-]+)(?:\\s+[^>]*)*>$").matcher(leftStripped)
                val closeTagMatch = Pattern.compile("^</([a-zA-Z1-6\\-]+)>").matcher(rightStripped)

                if (openTagMatch.find() && closeTagMatch.find()) {
                    val openTagName = openTagMatch.group(1)
                    val closeTagName = closeTagMatch.group(1)
                    if (openTagName.equals(closeTagName, ignoreCase = true)) {
                        isHtmlTagPair = true
                    }
                }
            }
        }

        val isBetweenPair = isPhpTagPair || isHtmlTagPair || (start > 0 && start < content.length && run {
            val charBefore = content[start - 1]
            val charAfter = content[start]
            (charBefore == '{' && charAfter == '}') ||
                    (charBefore == '(' && charAfter == ')') ||
                    (charBefore == '[' && charAfter == ']') ||
                    (charBefore == '"' && charAfter == '"') ||
                    (charBefore == '\'' && charAfter == '\'')
        })

        if (isBetweenPair && autoPairBraces) {
            val standardIndent = "\t"
            val insertion = "\n$leadingWhitespace$standardIndent\n$leadingWhitespace"

            isSelfModifyingText = true
            content.replace(start, start, insertion)
            applyTabReplacementSpans(content)
            runSyntaxHighlighter(content)
            isSelfModifyingText = false

            val targetSelection = start + 1 + leadingWhitespace.length + standardIndent.length
            setSelection(targetSelection)
            return true
        } else {
            val insertion = "\n$leadingWhitespace"

            isSelfModifyingText = true
            content.replace(start, start, insertion)
            applyTabReplacementSpans(content)
            runSyntaxHighlighter(content)
            isSelfModifyingText = false

            setSelection(start + insertion.length)
            return true
        }
    }

    private fun runSyntaxHighlighter(editable: Editable) {
        if (currentFileExtension == null) return

        val oldSpans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        for (span in oldSpans) { editable.removeSpan(span) }

        val textString = editable.toString()
        val length = textString.length

        var i = 0
        var contextMode = when (currentFileExtension) {
            "css" -> 1
            "js" -> 2
            "php" -> 0
            "java", "cs", "kt" -> 4
            "py" -> 5
            "json" -> 6
            "sql" -> 7
            else -> -1
        }

        if (contextMode == -1) return

        while (i < length) {
            if (currentFileExtension == "php" || currentFileExtension == "html") {
                if (textString.startsWith("<?php", i)) {
                    contextMode = 3
                    i += 5
                    continue
                } else if (contextMode == 3 && textString.startsWith("?>", i)) {
                    contextMode = 0
                    i += 2
                    continue
                }

                if (contextMode == 0) {
                    if (textString.startsWith("<script", i)) {
                        val closeTagEnd = textString.indexOf('>', i)
                        if (closeTagEnd != -1) {
                            colorizeHtmlTagBlock(editable, i, closeTagEnd + 1)
                            i = closeTagEnd + 1
                            contextMode = 2
                            continue
                        }
                    } else if (textString.startsWith("<style", i)) {
                        val closeTagEnd = textString.indexOf('>', i)
                        if (closeTagEnd != -1) {
                            colorizeHtmlTagBlock(editable, i, closeTagEnd + 1)
                            i = closeTagEnd + 1
                            contextMode = 1
                            continue
                        }
                    }
                } else if (contextMode == 2 && textString.startsWith("</script>", i)) {
                    contextMode = 0
                    colorizeHtmlTagBlock(editable, i, i + 9)
                    i += 9
                    continue
                } else if (contextMode == 1 && textString.startsWith("</style>", i)) {
                    contextMode = 0
                    colorizeHtmlTagBlock(editable, i, i + 8)
                    i += 8
                    continue
                }
            }

            val nextLineLimit = textString.indexOf('\n', i).let { if (it == -1) length else it }

            val textSliceLimit = nextLineLimit

            when (contextMode) {
                0 -> {
                    if (textString.startsWith("<!--", i)) {
                        val endComment = textString.indexOf("-->", i)
                        val endIdx = if (endComment != -1) endComment + 3 else length
                        editable.setSpan(ForegroundColorSpan(clrComment), i, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = endIdx
                    } else if (textString[i] == '<') {
                        val endTag = textString.indexOf('>', i)
                        val endIdx = if (endTag != -1) endTag + 1 else length
                        colorizeHtmlTagBlock(editable, i, endIdx)
                        i = endIdx
                    } else {
                        i++
                    }
                }
                1 -> {
                    if (textString.startsWith("/*", i)) {
                        val endComment = textString.indexOf("*/", i)
                        val endIdx = if (endComment != -1) endComment + 2 else length
                        editable.setSpan(ForegroundColorSpan(clrComment), i, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = endIdx
                    } else {
                        val matchedKeyword = findNextRegexMatch(cssKeywords, textString, i, textSliceLimit)
                        if (matchedKeyword != null) {
                            editable.setSpan(ForegroundColorSpan(clrKeyword), matchedKeyword.first, matchedKeyword.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = matchedKeyword.second
                        } else {
                            val literalMatch = findNextRegexMatch(genericLiterals, textString, i, textSliceLimit)
                            if (literalMatch != null) {
                                editable.setSpan(ForegroundColorSpan(clrLiteral), literalMatch.first, literalMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                i = literalMatch.second
                            } else {
                                i = colorizeGenericStringTokens(editable, textString, i, textSliceLimit)
                            }
                        }
                    }
                }
                2, 3, 4 -> {
                    val isPhp = contextMode == 3

                    if (textString.startsWith("//", i)) {
                        editable.setSpan(ForegroundColorSpan(clrComment), i, textSliceLimit, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = textSliceLimit
                    } else if (textString.startsWith("/*", i)) {
                        val endComment = textString.indexOf("*/", i)
                        val endIdx = if (endComment != -1) endComment + 2 else length
                        editable.setSpan(ForegroundColorSpan(clrComment), i, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = endIdx
                    }
                    else if (isPhp && textString.startsWith("<<<", i)) {
                        val tokenStart = i + 3
                        var tokenEnd = tokenStart
                        while (tokenEnd < textSliceLimit && (textString[tokenEnd].isLetterOrDigit() || textString[tokenEnd] == '_' || textString[tokenEnd] == '\'' || textString[tokenEnd] == '"')) {
                            tokenEnd++
                        }

                        val rawToken = textString.substring(tokenStart, tokenEnd)
                        val cleanToken = rawToken.replace("'", "").replace("\"", "").trim()

                        if (cleanToken.isNotEmpty()) {
                            editable.setSpan(ForegroundColorSpan(clrIdentifier), i, tokenEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                            i = textSliceLimit
                            var heredocIdx = i
                            var foundEnd = false

                            while (heredocIdx < length) {
                                val currentLineStart = heredocIdx
                                val currentLineEnd = textString.indexOf('\n', currentLineStart).let { if (it == -1) length else it }
                                val lineText = textString.substring(currentLineStart, currentLineEnd).trim()

                                if (lineText.startsWith(cleanToken)) {
                                    val tokenTerminationIdx = currentLineStart + textString.substring(currentLineStart, currentLineEnd).indexOf(cleanToken)

                                    val tokenEndIdx = tokenTerminationIdx + cleanToken.length
                                    val hasTrailingSemanticChars = tokenEndIdx < textString.length && (textString[tokenEndIdx].isLetterOrDigit() || textString[tokenEndIdx] == '_')

                                    if (!hasTrailingSemanticChars) {
                                        editable.setSpan(ForegroundColorSpan(clrIdentifier), tokenTerminationIdx, tokenEndIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                        i = currentLineEnd
                                        contextMode = 3
                                        foundEnd = true
                                        break
                                    }
                                }

                                if (cleanToken.equals("SQL", ignoreCase = true)) {
                                    val nextWordLimit = textString.indexOf('\n', heredocIdx).let { if (it == -1) length else it }
                                    val sqlMatch = findNextRegexMatch(sqlKeywords, textString, heredocIdx, nextWordLimit)
                                    if (sqlMatch != null) {
                                        editable.setSpan(ForegroundColorSpan(clrKeyword), sqlMatch.first, sqlMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                        heredocIdx = sqlMatch.second
                                        continue
                                    } else {
                                        editable.setSpan(ForegroundColorSpan(clrString), heredocIdx, heredocIdx + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    }
                                } else if (cleanToken.equals("HTML", ignoreCase = true)) {
                                    if (textString.startsWith("<!--", heredocIdx)) {
                                        val endComment = textString.indexOf("-->", heredocIdx)
                                        val endIdx = if (endComment != -1) endComment + 3 else currentLineEnd
                                        editable.setSpan(ForegroundColorSpan(clrComment), heredocIdx, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                        heredocIdx = endIdx
                                        continue
                                    } else if (textString[heredocIdx] == '<') {
                                        val endTag = textString.indexOf('>', heredocIdx)
                                        val endIdx = if (endTag != -1 && endTag < currentLineEnd) endTag + 1 else currentLineEnd
                                        colorizeHtmlTagBlock(editable, heredocIdx, endIdx)
                                        heredocIdx = endIdx
                                        continue
                                    }
                                } else {
                                    editable.setSpan(ForegroundColorSpan(clrString), currentLineStart, currentLineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    heredocIdx = currentLineEnd
                                    continue
                                }

                                heredocIdx++
                                if (heredocIdx > currentLineEnd) heredocIdx = currentLineEnd
                            }

                            if (foundEnd) continue
                        } else {
                            i += 3
                        }
                    } else if (isPhp && textString[i] == '$') {
                        var varEnd = i + 1
                        while (varEnd < length && (textString[varEnd].isLetterOrDigit() || textString[varEnd] == '_')) { varEnd++ }
                        editable.setSpan(ForegroundColorSpan(clrVariable), i, varEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = varEnd
                    } else {
                        val activeDict = when (currentFileExtension) {
                            "js" -> jsKeywords
                            "php" -> phpKeywords
                            "java" -> javaKeywords
                            "cs" -> csharpKeywords
                            else -> kotlinKeywords
                        }
                        val keywordMatch = findNextRegexMatch(activeDict, textString, i, textSliceLimit)
                        if (keywordMatch != null) {
                            editable.setSpan(ForegroundColorSpan(clrKeyword), keywordMatch.first, keywordMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = keywordMatch.second
                        } else {
                            val char = textString[i]
                            if (isPhp && (char == '"' || char == '\'')) {
                                var strEndIdx = i + 1
                                while (strEndIdx < textSliceLimit) {
                                    if (textString[strEndIdx] == char && textString[strEndIdx - 1] != '\\') {
                                        strEndIdx++
                                        break
                                    }
                                    strEndIdx++
                                }

                                val stringContent = textString.substring(i, strEndIdx)
                                if (stringContent.trim('"').trim('\'').trim().uppercase().startsWith("SELECT ") ||
                                    stringContent.trim('"').trim('\'').trim().uppercase().startsWith("INSERT ") ||
                                    stringContent.trim('"').trim('\'').trim().uppercase().startsWith("UPDATE ") ||
                                    stringContent.trim('"').trim('\'').trim().uppercase().startsWith("DELETE ")
                                ) {
                                    var sqlStrIdx = i
                                    while (sqlStrIdx < strEndIdx) {
                                        val queryMatch = findNextRegexMatch(sqlKeywords, textString, sqlStrIdx, martialLimit(sqlStrIdx, strEndIdx, textSliceLimit))
                                        if (queryMatch != null) {
                                            editable.setSpan(ForegroundColorSpan(clrKeyword), queryMatch.first, queryMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                            sqlStrIdx = queryMatch.second
                                        } else {
                                            if (textString[sqlStrIdx] != char) {
                                                editable.setSpan(ForegroundColorSpan(clrString), sqlStrIdx, sqlStrIdx + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                            }
                                            sqlStrIdx++
                                        }
                                    }
                                    i = strEndIdx
                                    continue
                                }
                            }

                            val literalMatch = findNextRegexMatch(genericLiterals, textString, i, textSliceLimit)
                            if (literalMatch != null) {
                                editable.setSpan(ForegroundColorSpan(clrLiteral), literalMatch.first, literalMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                i = literalMatch.second
                            } else {
                                i = colorizeGenericStringTokens(editable, textString, i, textSliceLimit)
                            }
                        }
                    }
                }
                5 -> {
                    if (textString[i] == '#') {
                        editable.setSpan(ForegroundColorSpan(clrComment), i, textSliceLimit, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = textSliceLimit
                    } else {
                        val isTripleQuote = textString.startsWith("\"\"\"", i) || textString.startsWith("'''", i)
                        if (isTripleQuote) {
                            val token = textString.substring(i, i + 3)
                            val endDocstring = textString.indexOf(token, i + 3)
                            val endIdx = if (endDocstring != -1) endDocstring + 3 else length
                            editable.setSpan(ForegroundColorSpan(clrComment), i, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = endIdx
                        } else {
                            val keywordMatch = findNextRegexMatch(pythonKeywords, textString, i, textSliceLimit)
                            if (keywordMatch != null) {
                                editable.setSpan(ForegroundColorSpan(clrKeyword), keywordMatch.first, keywordMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                i = keywordMatch.second
                            } else {
                                val literalMatch = findNextRegexMatch(genericLiterals, textString, i, textSliceLimit)
                                if (literalMatch != null) {
                                    editable.setSpan(ForegroundColorSpan(clrLiteral), literalMatch.first, literalMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    i = literalMatch.second
                                } else {
                                    i = colorizeGenericStringTokens(editable, textString, i, textSliceLimit)
                                }
                            }
                        }
                    }
                }
                6 -> {
                    val literalMatch = findNextRegexMatch(genericLiterals, textString, i, textSliceLimit)
                    if (literalMatch != null) {
                        editable.setSpan(ForegroundColorSpan(clrLiteral), literalMatch.first, literalMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = literalMatch.second
                    } else if (textString[i] == '"') {
                        var stringEndIdx = i + 1
                        while (stringEndIdx < textSliceLimit) {
                            if (textString[stringEndIdx] == '"' && textString[stringEndIdx - 1] != '\\') {
                                stringEndIdx++
                                break
                            }
                            stringEndIdx++
                        }

                        var lookAheadIdx = stringEndIdx
                        while (lookAheadIdx < length && (textString[lookAheadIdx] == ' ' || textString[lookAheadIdx] == '\t' || textString[lookAheadIdx] == '\n')) {
                            lookAheadIdx++
                        }

                        if (lookAheadIdx < length && textString[lookAheadIdx] == ':') {
                            editable.setSpan(ForegroundColorSpan(clrTag), i, stringEndIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        } else {
                            editable.setSpan(ForegroundColorSpan(clrString), i, stringEndIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        i = stringEndIdx
                    } else {
                        i++
                    }
                }
                7 -> {
                    if (textString.startsWith("--", i)) {
                        editable.setSpan(ForegroundColorSpan(clrComment), i, textSliceLimit, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = textSliceLimit
                    } else if (textString.startsWith("/*", i)) {
                        val endComment = textString.indexOf("*/", i)
                        val endIdx = if (endComment != -1) endComment + 2 else length
                        editable.setSpan(ForegroundColorSpan(clrComment), i, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = endIdx
                    } else {
                        val keywordMatch = findNextRegexMatch(sqlKeywords, textString, i, textSliceLimit)
                        if (keywordMatch != null) {
                            editable.setSpan(ForegroundColorSpan(clrKeyword), keywordMatch.first, keywordMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = keywordMatch.second
                        } else {
                            val literalMatch = findNextRegexMatch(genericLiterals, textString, i, textSliceLimit)
                            if (literalMatch != null) {
                                editable.setSpan(ForegroundColorSpan(clrLiteral), literalMatch.first, literalMatch.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                i = literalMatch.second
                            } else {
                                i = colorizeGenericStringTokens(editable, textString, i, textSliceLimit)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun martialLimit(idx: Int, end: Int, fallback: Int): Int {
        return if (end in (idx + 1)..fallback) end else fallback
    }

    private fun colorizeHtmlTagBlock(editable: Editable, start: Int, end: Int) {
        val segment = editable.substring(start, end)
        editable.setSpan(ForegroundColorSpan(clrTag), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val attrMatcher = Pattern.compile("\\s+([a-zA-Z\\-]+)\\s*=\\s*").matcher(segment)
        while (attrMatcher.find()) {
            editable.setSpan(ForegroundColorSpan(clrAttr), start + attrMatcher.start(1), start + attrMatcher.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val quoteMatcher = Pattern.compile("([\"'])(.*?)\\1").matcher(segment)
        while (quoteMatcher.find()) {
            editable.setSpan(ForegroundColorSpan(clrString), start + quoteMatcher.start(), start + quoteMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun colorizeGenericStringTokens(editable: Editable, text: String, currentIdx: Int, limit: Int): Int {
        val char = text[currentIdx]
        if (char == '"' || char == '\'') {
            var stringEndIdx = currentIdx + 1
            while (stringEndIdx < limit) {
                if (text[stringEndIdx] == char && text[stringEndIdx - 1] != '\\') {
                    stringEndIdx++
                    break
                }
                stringEndIdx++
            }
            editable.setSpan(ForegroundColorSpan(clrString), currentIdx, stringEndIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return stringEndIdx
        }
        return currentIdx + 1
    }

    private fun findNextRegexMatch(pattern: Pattern, text: String, start: Int, end: Int): Pair<Int, Int>? {
        val matcher = pattern.matcher(text).region(start, end)
        if (matcher.find() && matcher.start() == start) {
            val matchedEnd = matcher.end()

            if (start > 0) {
                val leadingChar = text[start - 1]
                if (leadingChar.isLetterOrDigit() || leadingChar == '_') {
                    return null
                }
            }

            if (matchedEnd < text.length) {
                val trailingChar = text[matchedEnd]
                if (trailingChar.isLetterOrDigit() || trailingChar == '_') {
                    return null
                }
            }

            return Pair(start, matchedEnd)
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handledByGesture = if (!wordWrapEnabled) gestureDetector.onTouchEvent(event) else false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isScrolling = false
                rollbackAnimator?.cancel()
                super.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (handledByGesture) {
                    val cancelEvent = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.onTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScrolling = false
                if (handledByGesture) {
                    checkAndTriggerRollback()
                    return true
                }
                super.onTouchEvent(event)
            }
        }

        return if (!wordWrapEnabled) {
            handledByGesture
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun bringPointIntoView(offset: Int): Boolean {
        if (isScrolling || (rollbackAnimator?.isRunning == true)) {
            return false
        }
        return super.bringPointIntoView(offset)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        if (isScrolling) return
        super.onSelectionChanged(selStart, selEnd)

        scanAndFindMatchingPairs()

        if (!wordWrapEnabled && selStart == selEnd && layout != null) {
            val currentLayout = layout ?: return
            val cursorX = currentLayout.getPrimaryHorizontal(selStart).toInt()

            val visibleLeft = scrollX + paddingLeft
            val visibleRight = scrollX + width - paddingRight

            if (cursorX < visibleLeft + 40) {
                val targetScrollX = (cursorX - paddingLeft - 80).coerceAtLeast(0)
                scrollTo(targetScrollX, scrollY)
            } else if (cursorX > visibleRight - 40) {
                val targetScrollX = cursorX - width + paddingRight + 120
                scrollTo(targetScrollX, scrollY)
            }
        }
        invalidate()
    }

    private fun checkAndTriggerRollback() {
        if (wordWrapEnabled) return
        val maxScrollX = (calculateMaxTextWidth() - width).coerceAtLeast(0)

        if (scrollX > maxScrollX || scrollX < 0) {
            val targetX = scrollX.coerceIn(0, maxScrollX)
            rollbackAnimator?.cancel()
            rollbackAnimator = ValueAnimator.ofInt(scrollX, targetX).apply {
                duration = 250
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Int
                    scrollTo(value, scrollY)
                }
                start()
            }
        }
    }

    override fun setTypeface(tf: android.graphics.Typeface?) {
        super.setTypeface(tf)
        placeholderPaint?.typeface = tf
        placeholderPaint?.textSize = this.textSize
        text?.let {
            applyTabReplacementSpans(it)
            runSyntaxHighlighter(it)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layout ?: return super.onDraw(canvas)

        canvas.save()
        if (highlightCurrentLine && !isReadOnly) {
            drawCurrentLineHighlight(canvas, layout)
        }

        if (matchedOpenIndex != -1 && matchedCloseIndex != -1) {
            drawCharacterHighlightBox(canvas, layout, matchedOpenIndex)
            drawCharacterHighlightBox(canvas, layout, matchedCloseIndex)
        }

        super.onDraw(canvas)
        canvas.restore()

        if (text.isNullOrEmpty() && !isReadOnly) drawPlaceholder(canvas)
        if (showLineNumbers) drawLineNumbers(canvas)
    }

    private fun drawCharacterHighlightBox(canvas: Canvas, layout: Layout, offset: Int) {
        val line = layout.getLineForOffset(offset)

        val left = layout.getPrimaryHorizontal(offset)
        val right = layout.getPrimaryHorizontal(offset + 1)

        val top = layout.getLineTop(line).toFloat() + paddingTop
        val bottom = layout.getLineBottom(line).toFloat() + paddingTop - lineSpacingExtra

        val rectLeft = left + paddingLeft
        val rectRight = right + paddingLeft
        val cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics)

        canvas.drawRoundRect(rectLeft, top, rectRight, bottom, cornerRadius, cornerRadius, pairBackgroundPaint)
        canvas.drawRoundRect(rectLeft, top, rectRight, bottom, cornerRadius, cornerRadius, pairBorderPaint)
    }

    private fun getCurrentLogicalLineRange(): Pair<Int, Int>? {
        val layout = layout ?: return null
        val content = text ?: return null
        val offset = selectionStart
        if (offset < 0) return null

        val currentVisualLine = layout.getLineForOffset(offset)

        var startVisualLine = currentVisualLine
        while (startVisualLine > 0) {
            val startOffset = layout.getLineStart(startVisualLine)
            if (content[startOffset - 1] == '\n') break
            startVisualLine--
        }

        var endVisualLine = currentVisualLine
        val totalLines = layout.lineCount
        while (endVisualLine < totalLines - 1) {
            val endOffset = layout.getLineEnd(endVisualLine)
            if (content[endOffset - 1] == '\n') break
            endVisualLine++
        }

        return Pair(startVisualLine, endVisualLine)
    }

    fun insertSymbolPair(openSymbol: String, closeSymbol: String) {
        if (isReadOnly) return
        val start = selectionStart
        val end = selectionEnd
        val textContent = text ?: return
        if (start < 0) return
        if (start != end) {
            val selectedText = textContent.subSequence(start, end)
            textContent.replace(start, end, "$openSymbol$selectedText$closeSymbol")
            setSelection(start + openSymbol.length, end + openSymbol.length)
        } else {
            textContent.insert(start, "$openSymbol$closeSymbol")
            setSelection(start + openSymbol.length)
        }
    }

    fun applyConfig(config: EditorConfig) {
        this.currentFileExtension = config.fileExtension
        showLineNumbers = config.showLineNumbers
        wordWrapEnabled = config.wordWrapEnabled
        highlightCurrentLine = config.highlightCurrentLine
        this.autoPairBraces = config.autoPairBraces

        // Switch typeface using the new cached assets
        applyTypefaceForExtension(config.fileExtension)

        text?.let { runSyntaxHighlighter(it) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyWordWrap() {
        isSingleLine = false
        maxLines = Int.MAX_VALUE

        if (wordWrapEnabled) {
            setHorizontallyScrolling(false)
            isHorizontalScrollBarEnabled = false

            breakStrategy = LineBreaker.BREAK_STRATEGY_BALANCED
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                lineBreakStyle = android.graphics.text.LineBreakConfig.LINE_BREAK_STYLE_STRICT
                lineBreakWordStyle = android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE
            }

            scrollTo(0, scrollY)
        } else {
            setHorizontallyScrolling(true)
            isHorizontalScrollBarEnabled = true

            breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
        updatePaddingAndLayout()
    }

    fun indentSelectedText() {
        if (isReadOnly) return
        val start = selectionStart
        val end = selectionEnd
        val content = text ?: return
        if (start < 0 || end < 0) return

        val indentBlock = "\t"

        if (start == end) {
            content.insert(start, indentBlock)
        } else {
            val startLine = layout.getLineForOffset(start)
            val endLine = layout.getLineForOffset(end)

            var currentOffsetShift = 0
            var newSelectionStart = start
            var newSelectionEnd = end

            val lineStartOffsets = mutableListOf<Int>()
            for (i in startLine..endLine) {
                lineStartOffsets.add(layout.getLineStart(i))
            }

            for (lineStart in lineStartOffsets) {
                val actualInsertPos = lineStart + currentOffsetShift
                content.insert(actualInsertPos, indentBlock)

                if (lineStart <= start) {
                    newSelectionStart += indentBlock.length
                }
                newSelectionEnd += indentBlock.length
                currentOffsetShift += indentBlock.length
            }

            setSelection(newSelectionStart.coerceIn(0, content.length), newSelectionEnd.coerceIn(0, content.length))
        }
    }

    fun deindentSelectedText() {
        if (isReadOnly) return
        val start = selectionStart
        val end = selectionEnd
        val content = text ?: return
        if (start < 0 || end < 0) return

        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset(end)

        var currentOffsetShift = 0
        var newSelectionStart = start
        var newSelectionEnd = end

        val lineBounds = mutableListOf<Pair<Int, Int>>()
        for (i in startLine..endLine) {
            lineBounds.add(Pair(layout.getLineStart(i), layout.getLineEnd(i)))
        }

        for ((lineStart, lineEnd) in lineBounds) {
            val actualStart = lineStart + currentOffsetShift
            val actualEnd = lineEnd + currentOffsetShift

            if (actualStart >= content.length) break

            var tokensToRemove = 0
            if (actualStart < actualEnd && content[actualStart] == '\t') {
                tokensToRemove = 1
            } else {
                while (tokensToRemove < 4 && (actualStart + tokensToRemove) < actualEnd && content[actualStart + tokensToRemove] == ' ') {
                    tokensToRemove++
                }
            }

            if (tokensToRemove > 0) {
                content.delete(actualStart, actualStart + tokensToRemove)

                if (lineStart <= start) {
                    newSelectionStart -= tokensToRemove
                    if (newSelectionStart < actualStart) newSelectionStart = actualStart
                }
                newSelectionEnd -= tokensToRemove
                currentOffsetShift -= tokensToRemove
            }
        }

        setSelection(newSelectionStart.coerceIn(0, content.length), newSelectionEnd.coerceIn(0, content.length))
    }


    private fun drawCurrentLineHighlight(canvas: Canvas, layout: android.text.Layout) {
        val range = getCurrentLogicalLineRange() ?: return
        val startLine = range.first
        val endLine = range.second

        val verticalOverflow = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)

        val spacingExtra = lineSpacingExtra
        val top = layout.getLineTop(startLine).toFloat() + paddingTop - verticalOverflow
        val bottom = layout.getLineBottom(endLine).toFloat() + paddingTop + verticalOverflow - spacingExtra

        val textMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics)
        val startX = totalLeftOffset.toFloat() - textMargin
        val endX = width.toFloat() - paddingRight.toFloat() + textMargin
        val cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics)

        canvas.save()
        canvas.translate(scrollX.toFloat(), 0f)
        canvas.drawRoundRect(startX, top, endX, bottom, cornerRadius, cornerRadius, currentLinePaint)
        canvas.restore()
    }

    private fun drawLineNumbers(canvas: Canvas) {
        val layout = layout ?: return

        // 1. Clipping Path (Keep your existing path logic)
        val path = android.graphics.Path()
        val cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
        path.addRoundRect(
            scrollX.toFloat(), scrollY.toFloat(),
            scrollX.toFloat() + width.toFloat(), scrollY.toFloat() + height.toFloat(),
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, cornerRadius, cornerRadius),
            android.graphics.Path.Direction.CW
        )

        canvas.save()
        canvas.clipPath(path)

        // 2. Draw Gutter Background
        canvas.drawRect(
            scrollX.toFloat(), scrollY.toFloat(),
            scrollX.toFloat() + gutterWidth.toFloat(), scrollY.toFloat() + height.toFloat(),
            gutterBackgroundPaint
        )

        val borderX = scrollX.toFloat() + gutterWidth.toFloat()
        canvas.drawLine(
            borderX, scrollY.toFloat(),
            borderX, scrollY.toFloat() + height.toFloat(),
            gutterBorderPaint
        )

        // 3. Stateless Line Number Drawing
        val firstVisibleLine = layout.getLineForVertical((scrollY - paddingTop).coerceAtLeast(0))
        val lastVisibleLine = layout.getLineForVertical((scrollY + height - paddingTop).coerceAtLeast(0))

        val metrics = lineNumberPaint.fontMetrics
        val fontHeightOffset = (metrics.descent + metrics.ascent) / 2f

        canvas.withTranslation(x = scrollX.toFloat()) {
            // Pre-calculate line numbers up to the first visible line to start the count correctly
            var currentLogicalLine = 1
            for (i in 0 until layout.lineCount) {
                val lineStart = layout.getLineStart(i)
                // A logical line is a NEW one if it's the start (0) or follows a '\n'
                val isStartOfLogicalLine = i == 0 || text?.get(lineStart - 1) == '\n'

                if (isStartOfLogicalLine && i < firstVisibleLine) {
                    currentLogicalLine++
                } else if (isStartOfLogicalLine && i in firstVisibleLine..lastVisibleLine) {
                    // This is a visible logical line, draw the number
                    val top = layout.getLineTop(i).toFloat() + paddingTop
                    val bottom = layout.getLineBottom(i).toFloat() + paddingTop
                    val centeredY = (top + bottom) / 2f - fontHeightOffset

                    this.drawText(
                        currentLogicalLine.toString(),
                        gutterWidth - 16f,
                        centeredY,
                        lineNumberPaint
                    )
                    currentLogicalLine++
                }
            }
        }
        canvas.restore()
    }

    private fun drawPlaceholder(canvas: Canvas) {
        val layout = layout ?: return
        val placeholderText = "Start typing..."
        val xPosition = scrollX.toFloat() + totalLeftOffset.toFloat()

        val top = layout.getLineTop(0).toFloat() + paddingTop
        val bottom = layout.getLineBottom(0).toFloat() + paddingTop - lineSpacingExtra
        val lineVerticalCenter = top + (bottom - top) / 2f
        val metrics = placeholderPaint.fontMetrics
        val fontHeightOffset = (metrics.descent + metrics.ascent) / 2f
        val centeredY = lineVerticalCenter - fontHeightOffset
        canvas.drawText(placeholderText, xPosition, centeredY, placeholderPaint)
    }

    private fun updatePaddingAndLayout() {
        super.setPadding(totalLeftOffset, 20, 20, 20)
        requestLayout()
        invalidate()
    }

    private fun applyTabReplacementSpans(editable: Editable) {
        val length = editable.length
        var i = 0
        while (i < length) {
            if (editable[i] == '\t') {
                val existingSpans = editable.getSpans(i, i + 1, TabReplacementSpan::class.java)
                if (existingSpans.isEmpty()) {
                    editable.setSpan(
                        TabReplacementSpan(),
                        i,
                        i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            i++
        }
    }

    private class TabReplacementSpan : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            if (fm != null) {
                val metrics = paint.fontMetricsInt
                fm.ascent = metrics.ascent
                fm.descent = metrics.descent
                fm.top = metrics.top
                fm.bottom = metrics.bottom
            }
            return paint.measureText("    ").toInt()
        }

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {}
    }
}