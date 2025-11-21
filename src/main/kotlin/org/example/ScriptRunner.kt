package org.example

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.stage.Stage

/**
 * Minimal JavaFX application used as a base for the script runner.
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

    override fun start(primaryStage: Stage) {
        val root = BorderPane().apply {
            center = Label("Kotlin Script Runner - skeleton")
        }

        val scene = Scene(root, 800.0, 600.0)

        primaryStage.title = "Kotlin Script Runner"
        primaryStage.scene = scene
        primaryStage.show()
    }
}
