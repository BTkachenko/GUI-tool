package org.example

import javafx.application.Application
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

/**
 * JavaFX application skeleton for the Kotlin script runner.
 * This step focuses only on the basic layout (editor, output, toolbar, status bar).
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
     * At this step the buttons only update the status text.
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
     * Temporary handler for the Run button.
     * The actual script execution will be implemented in the next step.
     */
    private fun onRunClicked() {
        statusLabel.text = "Status: (not implemented) Run clicked"
    }

    /**
     * Temporary handler for the Stop button.
     * The actual process termination will be implemented in the next step.
     */
    private fun onStopClicked() {
        statusLabel.text = "Status: (not implemented) Stop clicked"
    }
}
