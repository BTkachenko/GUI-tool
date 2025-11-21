package org.example

import javafx.application.Application
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.SplitPane
import javafx.scene.control.TextArea
import javafx.scene.control.ToolBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.LineNumberFactory
import org.fxmisc.richtext.model.StyleSpans
import org.fxmisc.richtext.model.StyleSpansBuilder
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * JavaFX application for running Kotlin scripts (.kts) using `kotlinc -script`.
 * Uses RichTextFX CodeArea for the editor with keyword highlighting, shows live output,
 * exit code indicator and an optional error list that appears only when errors are present.
 */
class ScriptRunnerApp : Application() {

    companion object {

        /**
         * List of Kotlin keywords to highlight.
         */
        private val KOTLIN_KEYWORDS: List<String> = listOf(
            "fun",
            "val",
            "var",
            "if",
            "else",
            "when",
            "for",
            "while",
            "return",
            "class",
            "object",
            "interface",
            "try",
            "catch",
            "finally",
            "throw",
            "true",
            "false",
            "null",
            "package",
            "import"
        )

        private val KEYWORD_PATTERN: Pattern = Pattern.compile(
            "(?<KEYWORD>\\b(" + KOTLIN_KEYWORDS.joinToString("|") + ")\\b)"
        )

        /**
         * Matches kotlinc error locations, e.g.:
         * /tmp/.../script.kts:2:5: error: unresolved reference: foo
         */
        private val ERROR_LOCATION_PATTERN: Pattern = Pattern.compile(
            "^(.+?):(\\d+):(\\d+):\\s+error:(.*)$"
        )

        /**
         * Application entry point.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            launch(ScriptRunnerApp::class.java, *args)
        }
    }

    private val executor: ExecutorService = Executors.newCachedThreadPool()

    @Volatile
    private var currentProcess: Process? = null

    private lateinit var editorArea: CodeArea
    private lateinit var outputArea: TextArea
    private lateinit var runButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusLabel: Label
    private lateinit var exitCodeLabel: Label
    private lateinit var errorListView: ListView<ScriptError>

    private val errorItems: ObservableList<ScriptError> = FXCollections.observableArrayList()

    override fun start(primaryStage: Stage) {
        editorArea = createEditorArea()
        outputArea = createOutputArea()
        errorListView = createErrorListView()

        val editorContainer = VirtualizedScrollPane(editorArea)

        val splitPane = SplitPane(editorContainer, outputArea).apply {
            orientation = Orientation.HORIZONTAL
            setDividerPositions(0.5)
        }

        val toolbar = createToolbar()
        val statusBar = createStatusBar()

        val centerContainer = VBox(splitPane, errorListView).apply {
            VBox.setVgrow(splitPane, Priority.ALWAYS)
            VBox.setVgrow(errorListView, Priority.NEVER)
        }

        val root = BorderPane().apply {
            top = toolbar
            center = centerContainer
            bottom = statusBar
        }

        val scene = Scene(root, 1000.0, 600.0)
        val cssUrl = javaClass.getResource("/application.css")
        if (cssUrl != null) {
            scene.stylesheets.add(cssUrl.toExternalForm())
        }

        primaryStage.title = "Kotlin Script Runner"
        primaryStage.scene = scene
        primaryStage.show()

        setRunningState(isRunning = false)
        updateStatus("Status: idle")
        updateExitCode(null)
        clearErrors()
        preloadSampleScript()
        applyHighlighting()
    }

    override fun stop() {
        currentProcess?.destroy()
        executor.shutdownNow()
    }

    /**
     * Creates the editor CodeArea used for script input, with line numbers and highlighting.
     */
    private fun createEditorArea(): CodeArea {
        val area = CodeArea()
        area.paragraphGraphicFactory = LineNumberFactory.get(area)
        area.styleClass.add("code-area")

        area.richChanges()
            .filter { change -> !change.isPlainTextIdentity }
            .subscribe {
                applyHighlighting()
            }

        HBox.setHgrow(area, Priority.ALWAYS)
        return area
    }

    /**
     * Creates the output text area used for displaying script output.
     */
    private fun createOutputArea(): TextArea {
        val area = TextArea()
        area.isEditable = false
        area.promptText = "Script output will appear here..."
        area.styleClass.add("output-area")
        HBox.setHgrow(area, Priority.ALWAYS)
        return area
    }

    /**
     * Creates the list view for displaying parsed compilation errors.
     * The list is hidden by default and only becomes visible when errors are added.
     */
    private fun createErrorListView(): ListView<ScriptError> {
        val listView = ListView<ScriptError>(errorItems)
        listView.styleClass.add("error-list")

        // Hidden by default.
        listView.isVisible = false
        listView.isManaged = false

        // Keep error panel small and non-intrusive.
        listView.prefHeight = 90.0
        listView.minHeight = 60.0
        listView.maxHeight = 110.0

        listView.setOnMouseClicked { event ->
            if (event.clickCount == 2) {
                val selected = listView.selectionModel.selectedItem ?: return@setOnMouseClicked
                navigateToError(selected)
            }
        }

        return listView
    }

    /**
     * Creates the toolbar with Run and Stop buttons.
     */
    private fun createToolbar(): ToolBar {
        runButton = Button("Run").apply {
            setOnAction { onRunClicked() }
        }

        stopButton = Button("Stop").apply {
            isDisable = true
            setOnAction { onStopClicked() }
        }

        val spacer = HBox().apply {
            HBox.setHgrow(this, Priority.ALWAYS)
        }

        return ToolBar(runButton, stopButton, spacer)
    }

    /**
     * Creates a status bar showing the current state and last exit code.
     */
    private fun createStatusBar(): HBox {
        statusLabel = Label("Status: idle")
        exitCodeLabel = Label("Last exit: n/a").apply {
            styleClass.add("exit-code-label")
        }

        val box = HBox(10.0, statusLabel, exitCodeLabel)
        box.alignment = Pos.CENTER_LEFT
        box.styleClass.add("status-bar")

        return box
    }

    /**
     * Handles the Run button click:
     * - writes the script to a temporary .kts file
     * - starts `kotlinc -script` process
     * - streams stdout and stderr to the output area
     */
    private fun onRunClicked() {
        if (currentProcess != null) {
            appendOutput("Process is already running.\n")
            return
        }

        outputArea.clear()
        clearErrors()
        updateStatus("Status: running")
        updateExitCode(null)

        val scriptText = editorArea.text
        val scriptFile: Path = try {
            writeScriptToTempFile(scriptText)
        } catch (ex: IOException) {
            updateStatus("Status: error writing script file")
            updateExitCode(-1)
            appendOutput("Error: Failed to write script file: ${ex.message}\n")
            return
        }

        setRunningState(isRunning = true)

        val processBuilder = ProcessBuilder(
            "kotlinc",
            "-script",
            scriptFile.toAbsolutePath().toString()
        )

        try {
            val process = processBuilder.start()
            currentProcess = process
            startReadingProcessOutput(process, scriptFile)
        } catch (ex: IOException) {
            currentProcess = null
            setRunningState(isRunning = false)
            updateStatus("Status: failed to start kotlinc")
            updateExitCode(-1)
            appendOutput("Error: Failed to start kotlinc process. Is kotlinc on PATH?\n")
            appendOutput("Cause: ${ex.javaClass.simpleName}: ${ex.message}\n")
            cleanupTempFile(scriptFile)
        }
    }

    /**
     * Handles the Stop button click: terminates the running process if any.
     */
    private fun onStopClicked() {
        val process = currentProcess ?: return

        process.destroy()
        currentProcess = null
        setRunningState(isRunning = false)
        updateStatus("Status: stopped by user")
        appendOutput("Process terminated by user.\n")
    }

    /**
     * Writes the current script content to a temporary .kts file.
     */
    private fun writeScriptToTempFile(content: String): Path {
        val tempDir = Files.createTempDirectory("kotlin-script-runner")
        val scriptFile = tempDir.resolve("script.kts")
        Files.writeString(scriptFile, content, StandardCharsets.UTF_8)
        return scriptFile
    }

    /**
     * Starts reading stdout and stderr on background threads,
     * and waits for process exit code on another task.
     */
    private fun startReadingProcessOutput(process: Process, scriptFile: Path) {
        // Read stdout
        executor.submit {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    appendOutput(line + "\n")
                }
            }
        }

        // Read stderr
        executor.submit {
            process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    handleErrorLine(line)
                    appendOutput("[err] $line\n")
                }
            }
        }

        // Wait for exit code
        executor.submit {
            val exitCode = try {
                process.waitFor()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                -1
            }

            currentProcess = null
            cleanupTempFile(scriptFile)

            Platform.runLater {
                setRunningState(isRunning = false)
                updateStatus("Status: finished (exit code = $exitCode)")
                updateExitCode(exitCode)
                appendOutput("Process finished with exit code $exitCode\n")
            }
        }
    }

    /**
     * Deletes the temporary script file and its parent directory if possible.
     */
    private fun cleanupTempFile(scriptFile: Path) {
        try {
            Files.deleteIfExists(scriptFile)
            val parent = scriptFile.parent
            if (parent != null) {
                Files.deleteIfExists(parent)
            }
        } catch (ex: IOException) {
            System.err.println("Failed to delete temp files: ${ex.message}")
        }
    }

    /**
     * Appends text to the output area on the JavaFX application thread.
     */
    private fun appendOutput(text: String) {
        Platform.runLater {
            outputArea.appendText(text)
        }
    }

    /**
     * Updates the status label text.
     */
    private fun updateStatus(text: String) {
        statusLabel.text = text
    }

    /**
     * Updates the exit code label with color indication.
     */
    private fun updateExitCode(exitCode: Int?) {
        exitCodeLabel.styleClass.removeAll("exit-ok", "exit-error")

        when (exitCode) {
            null -> {
                exitCodeLabel.text = "Last exit: n/a"
            }

            0 -> {
                exitCodeLabel.text = "Last exit: 0"
                exitCodeLabel.styleClass.add("exit-ok")
            }

            else -> {
                exitCodeLabel.text = "Last exit: $exitCode"
                exitCodeLabel.styleClass.add("exit-error")
            }
        }
    }

    /**
     * Sets run/stop button state based on whether a process is running.
     */
    private fun setRunningState(isRunning: Boolean) {
        runButton.isDisable = isRunning
        stopButton.isDisable = !isRunning
    }

    /**
     * Preloads a simple sample script to demonstrate long-running output.
     */
    private fun preloadSampleScript() {
        val sampleScript = """
            |println("Hello from Kotlin script")
            |for (i in 1..5) {
            |    println("Line ${'$'}i")
            |    Thread.sleep(500)
            |}
        """.trimMargin()

        editorArea.replaceText(sampleScript)
    }

    /**
     * Recomputes syntax highlighting for the current editor content.
     */
    private fun applyHighlighting() {
        val text = editorArea.text
        val highlighting = computeHighlighting(text)
        editorArea.setStyleSpans(0, highlighting)
    }

    /**
     * Computes style spans for Kotlin keyword highlighting.
     */
    private fun computeHighlighting(text: String): StyleSpans<MutableCollection<String>> {
        val matcher: Matcher = KEYWORD_PATTERN.matcher(text)
        var lastKwEnd = 0
        val spansBuilder = StyleSpansBuilder<MutableCollection<String>>()

        while (matcher.find()) {
            val styleClass: MutableCollection<String> =
                if (matcher.group("KEYWORD") != null) {
                    mutableListOf("keyword")
                } else {
                    mutableListOf()
                }

            val start = matcher.start()
            val end = matcher.end()

            if (start > lastKwEnd) {
                spansBuilder.add(mutableListOf(), start - lastKwEnd)
            }

            spansBuilder.add(styleClass, end - start)
            lastKwEnd = end
        }

        val remaining = text.length - lastKwEnd
        if (remaining > 0) {
            spansBuilder.add(mutableListOf(), remaining)
        }

        return spansBuilder.create()
    }

    /**
     * Clears the current list of errors and hides the error list view.
     */
    private fun clearErrors() {
        errorItems.clear()
        errorListView.isVisible = false
        errorListView.isManaged = false
    }

    /**
     * Parses potential error location lines from kotlinc stderr.
     */
    private fun handleErrorLine(line: String) {
        val matcher = ERROR_LOCATION_PATTERN.matcher(line)
        if (!matcher.find()) {
            return
        }

        val lineNumber = matcher.group(2).toIntOrNull() ?: return
        val columnNumber = matcher.group(3).toIntOrNull() ?: 1
        val message = matcher.group(4).trim()

        val error = ScriptError(
            line = lineNumber,
            column = columnNumber,
            message = message,
            rawLine = line
        )

        Platform.runLater {
            errorItems.add(error)

            if (!errorListView.isVisible) {
                errorListView.isVisible = true
                errorListView.isManaged = true
            }
        }
    }

    /**
     * Moves the caret to the error location in the editor and scrolls it into view.
     */
    private fun navigateToError(error: ScriptError) {
        if (editorArea.paragraphs.isEmpty()) {
            return
        }

        val requestedLineIndex = (error.line - 1).coerceAtLeast(0)
        val maxLineIndex = editorArea.paragraphs.size - 1
        val safeLineIndex = requestedLineIndex.coerceAtMost(maxLineIndex)

        // Run later so focus change happens *after* the mouse event on the ListView is processed.
        Platform.runLater {
            try {
                editorArea.requestFocus()
                editorArea.moveTo(safeLineIndex, 0)
                editorArea.requestFollowCaret()
            } catch (ex: Exception) {
                System.err.println(
                    "Failed to navigate to error at line ${error.line}: ${ex.message}"
                )
            }
        }
    }

    data class ScriptError(
        val line: Int,
        val column: Int,
        val message: String,
        val rawLine: String
    ) {

        /**
         * Human-readable representation shown in the list view.
         */
        override fun toString(): String {
            return "Line $line, Col $column: $message"
        }
    }
}
