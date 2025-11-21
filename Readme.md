# Kotlin Script Runner

A small desktop GUI tool to edit and run Kotlin script files (`.kts`) and see their output live, side by side.

- Left pane: Kotlin script editor with line numbers and keyword highlighting.
- Right pane: Live output of `kotlinc -script` (stdout and stderr).
- Bottom: Status bar indicating whether a script is running and the exit code of the last run.

Scripts are executed using:

```bash
kotlinc -script script.kts
```

> The tool is implemented in Kotlin with JavaFX and RichTextFX.

---

## Requirements

- JDK 17+ (project uses JDK 21 as toolchain).
- Kotlin compiler (`kotlinc`) available on `PATH`.
- Internet access on the first run so Gradle can download dependencies.

The tool is primarily tested on Linux (Ubuntu). It should also work on other platforms where JavaFX and `kotlinc` are available.

---

## Build and Run

From the project root:

```bash
./gradlew run
```

On Windows:

```bat
gradlew.bat run
```

This runs the JavaFX application via the Gradle `run` task, so JavaFX modules are added automatically.

To build a jar:

```bash
./gradlew jar
```

Main class:

```text
org.example.ScriptRunnerApp
```

You can also run it from IntelliJ IDEA:

1. Open the project as a Gradle project.
2. Wait for Gradle sync.
3. In the Gradle tool window, run: `Tasks → application → run`.

---

## Using the Tool

1. Start the application.
2. Edit the Kotlin script in the left pane (a small sample script is preloaded).
3. Press **Run**:
    - The script is written to a temporary `script.kts` file.
    - The tool runs: `kotlinc -script /path/to/script.kts`.
    - Stdout appears in the right pane.
    - Stderr lines are prefixed with `[err]`.
    - The status bar shows `Status: running`.
4. When the process finishes:
    - The status bar shows `Status: finished (exit code = X)`.
    - The **Last exit** label turns green for `0`, red for non-zero exit codes.
5. Press **Stop** to terminate a long-running script. The process is destroyed and the status changes to `Status: stopped by user`.

---

## Syntax Highlighting

The editor uses [RichTextFX](https://github.com/FXMisc/RichTextFX) `CodeArea`:

- Line numbers are shown on the left.
- A simple regex-based highlighter colors common Kotlin keywords:
    - `fun`, `val`, `var`, `if`, `else`, `for`, `while`, `when`, `class`, `object`,
      `interface`, `try`, `catch`, `finally`, `throw`, `true`, `false`, `null`, `package`, `import`.
- Highlighting is updated on each text change and can be easily extended.

---

## Implementation Notes

- Script execution is done via `ProcessBuilder("kotlinc", "-script", scriptPath)`.
- The process is started on a background thread using an `ExecutorService`.
- Stdout and stderr are read on separate background tasks and appended to the output pane via `Platform.runLater`.
- UI state (Run/Stop buttons, status text, last exit code) is always updated on the JavaFX application thread.
- Temporary files are created with `Files.createTempDirectory` and cleaned up on a best-effort basis after each run.