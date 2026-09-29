package com.veritas.desktop.system

import java.awt.Color
import java.awt.Font
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

object DesktopSystemTray {
    private var trayIcon: TrayIcon? = null

    fun initialize(
        onOpenWorkstation: () -> Unit,
        onOpenFloater: () -> Unit,
        onReadClipboard: () -> Unit,
        onTogglePlay: () -> Unit,
        onExit: () -> Unit
    ) {
        if (!SystemTray.isSupported()) return

        try {
            val systemTray = SystemTray.getSystemTray()

            // Generate an elegant V icon dynamically if no asset file
            val image = createTrayIconImage()

            val popup = PopupMenu()

            val itemWorkstation = MenuItem("Open Veritas Reader").apply {
                font = Font(Font.SANS_SERIF, Font.BOLD, 12)
                addActionListener { SwingUtilities.invokeLater { onOpenWorkstation() } }
            }
            val itemFloater = MenuItem("Open Floating Quick-Reader (Ctrl+Alt+V)").apply {
                addActionListener { SwingUtilities.invokeLater { onOpenFloater() } }
            }
            val itemReadClipboard = MenuItem("Read Highlighted Text (Alt+R)").apply {
                addActionListener { SwingUtilities.invokeLater { onReadClipboard() } }
            }
            val itemTogglePlay = MenuItem("Play / Pause").apply {
                addActionListener { SwingUtilities.invokeLater { onTogglePlay() } }
            }
            val itemExit = MenuItem("Exit Veritas Reader").apply {
                addActionListener { SwingUtilities.invokeLater { onExit() } }
            }

            popup.add(itemWorkstation)
            popup.add(itemFloater)
            popup.addSeparator()
            popup.add(itemReadClipboard)
            popup.add(itemTogglePlay)
            popup.addSeparator()
            popup.add(itemExit)

            trayIcon = TrayIcon(image, "Veritas Reader", popup).apply {
                isImageAutoSize = true
                addActionListener {
                    SwingUtilities.invokeLater { onOpenWorkstation() }
                }
            }

            systemTray.add(trayIcon)
        } catch (e: Exception) {
            // Ignore tray errors
        }
    }

    private fun createTrayIconImage(): Image {
        val size = 32
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Gradient background
        g2.color = Color(33, 150, 243)
        g2.fillRoundRect(2, 2, size - 4, size - 4, 8, 8)

        // White 'V' symbol
        g2.color = Color.WHITE
        g2.font = Font("SansSerif", Font.BOLD, 20)
        val fm = g2.fontMetrics
        val str = "V"
        val x = (size - fm.stringWidth(str)) / 2
        val y = ((size - fm.height) / 2) + fm.ascent
        g2.drawString(str, x, y - 1)

        g2.dispose()
        return image
    }
}
