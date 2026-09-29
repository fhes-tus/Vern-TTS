package com.veritas.desktop.system

import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent

object ClipboardHelper {

    fun getClipboardText(): String {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                clipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun setClipboardText(text: String) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        } catch (e: Exception) {}
    }

    fun captureSelectionFromActiveApp(): String {
        // Save current clipboard
        val previousClipboard = getClipboardText()

        try {
            // Simulate Ctrl+C to copy selected text
            val robot = Robot()
            robot.keyPress(KeyEvent.VK_CONTROL)
            robot.keyPress(KeyEvent.VK_C)
            robot.keyRelease(KeyEvent.VK_C)
            robot.keyRelease(KeyEvent.VK_CONTROL)

            Thread.sleep(120) // Brief pause for clipboard update
            val selected = getClipboardText()
            if (selected.isNotBlank() && selected != previousClipboard) {
                return selected
            }
        } catch (e: Exception) {
            // Ignore
        }

        return getClipboardText()
    }
}

object GlobalHotkeyManager {
    private const val HOTKEY_ID_READ_SELECTION = 1001
    private const val HOTKEY_ID_TOGGLE_FLOATER = 1002

    private const val MOD_ALT = 0x0001
    private const val MOD_CONTROL = 0x0002
    private const val MOD_SHIFT = 0x0004
    private const val MOD_NOREPEAT = 0x4000

    private const val VK_R = 0x52
    private const val VK_SPACE = 0x20
    private const val VK_V = 0x56

    private var isListening = false
    private var listenerThread: Thread? = null

    var onReadSelectionTriggered: (() -> Unit)? = null
    var onToggleFloaterTriggered: (() -> Unit)? = null

    fun start() {
        if (isListening || !Platform.isWindows()) return
        isListening = true

        listenerThread = Thread {
            try {
                val user32 = User32.INSTANCE

                // Register Alt+R to Read Selection
                user32.RegisterHotKey(
                    null,
                    HOTKEY_ID_READ_SELECTION,
                    MOD_ALT or MOD_NOREPEAT,
                    VK_R
                )

                // Register Ctrl+Alt+V to Toggle Floater
                user32.RegisterHotKey(
                    null,
                    HOTKEY_ID_TOGGLE_FLOATER,
                    MOD_CONTROL or MOD_ALT or MOD_NOREPEAT,
                    VK_V
                )

                val msg = WinUser.MSG()
                while (isListening && user32.GetMessage(msg, null, 0, 0) != 0) {
                    if (msg.message == WinUser.WM_HOTKEY) {
                        val id = msg.wParam.toInt()
                        when (id) {
                            HOTKEY_ID_READ_SELECTION -> {
                                javax.swing.SwingUtilities.invokeLater {
                                    onReadSelectionTriggered?.invoke()
                                }
                            }
                            HOTKEY_ID_TOGGLE_FLOATER -> {
                                javax.swing.SwingUtilities.invokeLater {
                                    onToggleFloaterTriggered?.invoke()
                                }
                            }
                        }
                    }
                    user32.TranslateMessage(msg)
                    user32.DispatchMessage(msg)
                }

                user32.UnregisterHotKey(null, HOTKEY_ID_READ_SELECTION)
                user32.UnregisterHotKey(null, HOTKEY_ID_TOGGLE_FLOATER)
            } catch (e: Throwable) {
                // Fallback gracefully if JNA fails on some systems
            }
        }.apply {
            isDaemon = true
            name = "Veritas-Global-Hotkey-Listener"
            start()
        }
    }

    fun stop() {
        isListening = false
        listenerThread?.interrupt()
        listenerThread = null
    }
}
