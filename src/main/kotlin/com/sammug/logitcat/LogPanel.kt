package com.sammug.logitcat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.text.*

/**
 * LogPanel — streams all log lines with Logcat-style level + text filtering.
 *
 *  Toolbar:  [V][D][I][W][E][F]  [Tag: ___________]  [Search: ___________]  [Clear] [Auto-scroll ☑]
 *  Body:     coloured log lines in a monospace JTextPane
 */
class LogPanel(private val project: Project) : JPanel(BorderLayout()) {

    // ── Colours ───────────────────────────────────────────────────────────────
    private val COL_V = JBColor(Color(0x7d8590), Color(0x7d8590))
    private val COL_D = JBColor(Color(0x58a6ff), Color(0x58a6ff))
    private val COL_I = JBColor(Color(0x3fb950), Color(0x3fb950))
    private val COL_W = JBColor(Color(0xe3b341), Color(0xe3b341))
    private val COL_E = JBColor(Color(0xf85149), Color(0xf85149))
    private val COL_F = JBColor(Color(0xff6b6b), Color(0xff6b6b))
    private val COL_DEFAULT = JBColor(Color(0xadbac7), Color(0xadbac7))
    private val COL_BG = JBColor(Color(0x0d1117), Color(0x0d1117))

    // ── State ─────────────────────────────────────────────────────────────────
    private val MAX_LINES = 5000
    private val allLines  = ArrayDeque<LogLine>(MAX_LINES + 1)
    private val activeLevels = mutableSetOf('V','D','I','W','E','F')

    private var tagFilter    = ""
    private var searchFilter = ""
    private var autoScroll   = true

    // ── UI ────────────────────────────────────────────────────────────────────
    private val textPane   = JTextPane()
    private val scrollPane = JBScrollPane(textPane)
    private val tagField   = JTextField(14)
    private val searchField= JTextField(16)
    private val levelBtns  = mutableMapOf<Char, JToggleButton>()
    private val statusLbl  = JLabel("● 0 lines")

    // SSE client
    private var logClient: LogStreamClient? = null

    init {
        build()
        connect()
    }

    private fun build() {
        // ── Text pane ─────────────────────────────────────────────────────────
        textPane.isEditable = false
        textPane.background = COL_BG
        textPane.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(12f).toInt())
        textPane.border = JBUI.Borders.empty(4, 8)

        // ── Toolbar ───────────────────────────────────────────────────────────
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        toolbar.background = JBColor(Color(0x161b22), Color(0x161b22))
        toolbar.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(Color(0x30363d), Color(0x30363d)), 0, 0, 1, 0),
            JBUI.Borders.empty(3, 6)
        )

        // Level toggle buttons
        val levels = listOf('V' to COL_V, 'D' to COL_D, 'I' to COL_I,
                            'W' to COL_W, 'E' to COL_E, 'F' to COL_F)
        toolbar.add(JLabel("Level: ").also { it.foreground = JBColor.GRAY; it.font = it.font.deriveFont(11f) })
        for ((ch, col) in levels) {
            val btn = JToggleButton(ch.toString()).apply {
                isSelected = true
                font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11f).toInt())
                foreground = col
                background = JBColor(Color(0x21262d), Color(0x21262d))
                border = JBUI.Borders.customLine(JBColor(Color(0x30363d), Color(0x30363d)))
                preferredSize = Dimension(28, 22)
                toolTipText = levelName(ch)
                addActionListener { activeLevels.set(ch, isSelected); refilter(); reconnectIfLevelChanged() }
            }
            levelBtns[ch] = btn
            toolbar.add(btn)
        }

        toolbar.add(Box.createHorizontalStrut(8))

        // Tag filter
        toolbar.add(JLabel("Tag: ").also { it.foreground = JBColor.GRAY; it.font = it.font.deriveFont(11f) })
        tagField.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        tagField.background = JBColor(Color(0x161b22), Color(0x161b22))
        tagField.foreground = JBColor.WHITE
        tagField.caretColor = JBColor.WHITE
        tagField.border = JBUI.Borders.customLine(JBColor(Color(0x30363d), Color(0x30363d)))
        tagField.toolTipText = "Filter by tag (e.g. OkHttp, MainActivity)"
        tagField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { tagFilter = tagField.text; refilter() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { tagFilter = tagField.text; refilter() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {}
        })
        toolbar.add(tagField)

        toolbar.add(Box.createHorizontalStrut(6))

        // Text search
        toolbar.add(JLabel("Search: ").also { it.foreground = JBColor.GRAY; it.font = it.font.deriveFont(11f) })
        searchField.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        searchField.background = JBColor(Color(0x161b22), Color(0x161b22))
        searchField.foreground = JBColor.WHITE
        searchField.caretColor = JBColor.WHITE
        searchField.border = JBUI.Borders.customLine(JBColor(Color(0x30363d), Color(0x30363d)))
        searchField.toolTipText = "Search log messages"
        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { searchFilter = searchField.text; refilter() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { searchFilter = searchField.text; refilter() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {}
        })
        toolbar.add(searchField)

        toolbar.add(Box.createHorizontalStrut(6))

        // Clear button
        val clearBtn = JButton("Clear").apply {
            font = font.deriveFont(11f)
            toolTipText = "Clear log output"
            addActionListener { clearLogs() }
        }
        toolbar.add(clearBtn)

        // Auto-scroll toggle
        val autoScrollBtn = JToggleButton("↓").apply {
            isSelected = true
            toolTipText = "Auto-scroll to bottom"
            font = font.deriveFont(11f)
            preferredSize = Dimension(28, 22)
            addActionListener { autoScroll = isSelected }
        }
        toolbar.add(autoScrollBtn)

        // Status
        statusLbl.font = statusLbl.font.deriveFont(10f)
        statusLbl.foreground = JBColor.GRAY
        statusLbl.border = JBUI.Borders.emptyLeft(8)
        toolbar.add(statusLbl)

        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    // ── Log ingestion ─────────────────────────────────────────────────────────

    fun addLine(line: LogLine) {
        ApplicationManager.getApplication().invokeLater {
            synchronized(allLines) {
                allLines.addLast(line)
                if (allLines.size > MAX_LINES) allLines.removeFirst()
            }
            if (matchesFilter(line)) appendToPane(line)
            updateStatus()
        }
    }

    private fun matchesFilter(line: LogLine): Boolean {
        val lc = line.levelChar()
        if (lc !in activeLevels) return false
        if (tagFilter.isNotBlank() && !line.tag.contains(tagFilter, ignoreCase = true) &&
            !line.source.contains(tagFilter, ignoreCase = true)) return false
        if (searchFilter.isNotBlank() && !line.message.contains(searchFilter, ignoreCase = true) &&
            !line.raw.contains(searchFilter, ignoreCase = true)) return false
        return true
    }

    private fun refilter() {
        val doc = DefaultStyledDocument()
        synchronized(allLines) {
            allLines.filter { matchesFilter(it) }.forEach { appendLine(doc, it) }
        }
        textPane.document = doc
        updateStatus()
        if (autoScroll) scrollToBottom()
    }

    private fun appendToPane(line: LogLine) {
        appendLine(textPane.styledDocument, line)
        if (autoScroll) scrollToBottom()
    }

    private fun appendLine(doc: StyledDocument, line: LogLine) {
        val lc  = line.levelChar()
        val col = levelColor(lc)
        val ts  = line.time.substringAfter('T').substringBeforeLast('+').take(12)
        val tag = line.tag.ifBlank { line.source }.take(20).padEnd(20)
        val txt = "$ts  $lc  $tag  ${line.message}\n"

        val style = doc.addStyle(null, null)
        StyleConstants.setForeground(style, col)
        StyleConstants.setFontFamily(style, Font.MONOSPACED)
        StyleConstants.setFontSize(style, JBUI.scaleFontSize(11f).toInt())
        try { doc.insertString(doc.length, txt, style) } catch (_: Exception) {}
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val bar = scrollPane.verticalScrollBar
            bar.value = bar.maximum
        }
    }

    private fun clearLogs() {
        synchronized(allLines) { allLines.clear() }
        textPane.document = DefaultStyledDocument()
        updateStatus()
    }

    private fun updateStatus() {
        val visible = textPane.document.length
        statusLbl.text = "● ${allLines.size} lines"
    }

    // ── SSE connection ────────────────────────────────────────────────────────

    /** The lowest level currently enabled — sent to server as ?level= param */
    private fun minActiveLevel(): String {
        val order = listOf('V','D','I','W','E','F')
        return order.firstOrNull { it in activeLevels }?.toString() ?: "V"
    }

    private fun connect() {
        val settings = LogitCatSettings.getInstance()
        logClient?.disconnect()
        logClient = LogStreamClient(
            port      = settings.dashboardPort,
            minLevel  = minActiveLevel(),
            onLine    = { addLine(it) },
            onConnected    = {},
            onDisconnected = {}
        )
        logClient?.connect()
    }

    /** Reconnect only if the effective minimum level changed (avoids reconnect on every click) */
    private var lastMinLevel = "V"
    private fun reconnectIfLevelChanged() {
        val newMin = minActiveLevel()
        if (newMin != lastMinLevel) {
            lastMinLevel = newMin
            connect()
        }
    }

    fun reconnect() { connect() }

    fun dispose() { logClient?.disconnect() }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun levelColor(ch: Char) = when (ch) {
        'V' -> COL_V; 'D' -> COL_D; 'I' -> COL_I
        'W' -> COL_W; 'E' -> COL_E; 'F' -> COL_F
        else -> COL_DEFAULT
    }

    private fun levelName(ch: Char) = when (ch) {
        'V' -> "Verbose"; 'D' -> "Debug"; 'I' -> "Info"
        'W' -> "Warn"; 'E' -> "Error"; 'F' -> "Fatal/Assert"
        else -> ch.toString()
    }

    private fun MutableSet<Char>.set(key: Char, value: Boolean) {
        if (value) add(key) else remove(key)
    }
}
