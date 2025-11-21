package org.example

import javafx.application.Application
import javafx.application.Application.launch
import javafx.application.Platform
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.SplitPane
import javafx.scene.control.TextArea
import javafx.scene.control.ToolBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.stage.Stage
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * JavaFX application for running Kotlin scripts (.kts) using `kotlinc -script`.
 */
class ScriptRunnerApp : Application() {

    companion object {
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

    private lateinit var editorArea: TextArea
    private lateinit var outputArea: TextArea
    private lateinit var runButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusLabel: Label

    override fun start(primaryStage: Stage) {
        editorArea = createEditorArea()
        outputArea = createOutputArea()

        val splitPane = SplitPane(editorArea, outputArea).apply {
            orientation = Orientation.HORIZONTAL
            setDividerPositions(0.5)
        }

        val toolbar = createToolbar()
        val statusBar = createStatusBar()

        val root = BorderPane().apply {
            top = toolbar
            center = splitPane
            bottom = statusBar
        }

        val scene = Scene(root, 1000.0, 600.0)

        primaryStage.title = "Kotlin Script Runner"
        primaryStage.scene = scene
        primaryStage.show()

        setRunningState(isRunning = false)
        updateStatus("Status: idle")
        preloadSampleScript()
    }

    override fun stop() {
        currentProcess?.destroy()
        executor.shutdownNow()
    }

    /**
     * Creates the editor text area used for script input.
     */
    private fun createEditorArea(): TextArea {
        val area = TextArea()
        area.promptText = "Write your Kotlin script here..."
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
        HBox.setHgrow(area, Priority.ALWAYS)
        return area
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
     * Creates a simple status bar showing the current state.
     */
    private fun createStatusBar(): HBox {
        statusLabel = Label("Status: idle")

        val box = HBox(10.0, statusLabel)
        box.alignment = Pos.CENTER_LEFT
        box.style = "-fx-padding: 4 8 4 8;"

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
        updateStatus("Status: running")

        val scriptText = editorArea.text
        val scriptFile: Path = try {
            writeScriptToTempFile(scriptText)
        } catch (ex: IOException) {
            updateStatus("Status: error writing script file")
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

        editorArea.text = sampleScript
    }
}
