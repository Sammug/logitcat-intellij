package com.sammug.logitcat

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.*
import javax.swing.event.DocumentListener

/**
 * LogPanel — Logcat-style log view:
 * - JList with custom cell renderer (virtual/lazy — only visible rows painted → no freeze)
 * - Incoming lines queued off-EDT, batched onto model every 50 ms
 * - Level toggle buttons, tag filter, text search
 * - Columns: timestamp | pid-tid | tag | ▌level | message
 */
class LogPanel(private val project: Project) : JPanel(BorderLayout()) {

    // ── Level colours (matching IntelliJ Darcula + AS Logcat) ────────────────
    companion object {
        val C_V = JBColor(Color(0x7d8590), Color(0x8b949e))
        val C_D = JBColor(Color(0x4a86c8), Color(0x6897bb))
        val C_I = JBColor(Color(0x4e9a50), Color(0x6a8759))
        val C_W = JBColor(Color(0xbbaa00), Color(0xe3b341))
        val C_E = JBColor(Color(0xcc3333), Color(0xf85149))
        val C_F = JBColor(Color(0xff4444), Color(0xff6b6b))
        val C_DEF = JBColor(Color(0xadbac7), Color(0xadbac7))
        val BG = JBColor(Color(0x1e1e1e), Color(0x1e1e1e))
        val BG_SEL = JBColor(Color(0x2d4a6e), Color(0x2d4a6e))
        val C_META = JBColor(Color(0x6e7681), Color(0x6e7681))

        fun levelColor(ch: Char) = when (ch) {
            'V' -> C_V; 'D' -> C_D; 'I' -> C_I
            'W' -> C_W; 'E' -> C_E; 'F' -> C_F
            else -> C_DEF
        }
        fun levelName(ch: Char) = when (ch) {
            'V' -> "Verbose"; 'D' -> "Debug"; 'I' -> "Info"
            'W' -> "Warn";    'E' -> "Error"; 'F' -> "Fatal"
            else -> ch.toString()
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private val MAX_LINES    = 10_000
    private val allLines     = ArrayDeque<ParsedLogLine>(MAX_LINES + 64)  // full buffer
    private val activeLevels = mutableSetOf('V','D','I','W','E','F')
    private var tagFilter    = ""
    private var textFilter   = ""
    private var autoScroll   = true
    private var lastMinLevel = "V"

    // Off-EDT queue — background threads drop lines here
    private val pending = ConcurrentLinkedQueue<ParsedLogLine>()

    // ── Model & list ──────────────────────────────────────────────────────────
    private val model      = DefaultListModel<ParsedLogLine>()
    private val logList    = JBList(model)
    private val scrollPane = JBScrollPane(logList)

    // ── Toolbar widgets ───────────────────────────────────────────────────────
    private val levelBtns   = mutableMapOf<Char, JToggleButton>()
    private val tagField    = JTextField(12)
    private val searchField = JTextField(16)
    private val statusLbl   = JLabel("0 lines")

    // Flush timer — drains pending queue onto model every 50 ms on the EDT
    private val flushTimer = Timer(50) { flushPending() }

    // SSE client
    private var logClient: LogStreamClient? = null

    init {
        buildUI()
        flushTimer.start()
        connect()
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private fun buildUI() {
        background = BG

        // ── List ──────────────────────────────────────────────────────────────
        logList.cellRenderer = LogCellRenderer()
        logList.background   = BG
        logList.selectionBackground = BG_SEL
        logList.selectionForeground = Color.WHITE
        logList.fixedCellHeight = JBUI.scale(18)
        logList.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11.5f).toInt())

        // ── Toolbar ───────────────────────────────────────────────────────────
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 3, 2))
        toolbar.background = JBColor(Color(0x161b22), Color(0x161b22))
        toolbar.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(Color(0x30363d), Color(0x30363d)), 0, 0, 1, 0),
            JBUI.Borders.empty(2, 4)
        )

        // Level buttons
        for ((ch, col) in listOf('V' to C_V, 'D' to C_D, 'I' to C_I, 'W' to C_W, 'E' to C_E, 'F' to C_F)) {
            val btn = JToggleButton(ch.toString()).apply {
                isSelected   = true
                font         = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(10.5f).toInt())
                foreground   = col
                background   = JBColor(Color(0x21262d), Color(0x21262d))
                border       = JBUI.Borders.customLine(JBColor(Color(0x30363d), Color(0x30363d)))
                preferredSize= Dimension(JBUI.scale(24), JBUI.scale(20))
                toolTipText  = levelName(ch)
                isFocusPainted = false
                addActionListener { activeLevels.set(ch, isSelected); refilter(); reconnectIfLevelChanged() }
            }
            levelBtns[ch] = btn
            toolbar.add(btn)
        }

        toolbar.add(Box.createHorizontalStrut(6))

        // Tag filter
        toolbar.add(label("Tag:"))
        styleInput(tagField)
        tagField.toolTipText = "Filter by tag"
        tagField.document.addDocumentListener(quickFilter { tagFilter = tagField.text; refilter() })
        toolbar.add(tagField)

        toolbar.add(Box.createHorizontalStrut(4))

        // Text search
        toolbar.add(label("Search:"))
        styleInput(searchField)
        searchField.toolTipText = "Search messages"
        searchField.document.addDocumentListener(quickFilter { textFilter = searchField.text; refilter() })
        toolbar.add(searchField)

        toolbar.add(Box.createHorizontalStrut(4))

        // Clear
        val clearBtn = JButton("✕ Clear").apply {
            font = font.deriveFont(11f); isFocusPainted = false
            addActionListener { clearLogs() }
        }
        toolbar.add(clearBtn)

        // Auto-scroll
        val asBtn = JToggleButton("↓ Scroll").apply {
            isSelected = true; font = font.deriveFont(11f); isFocusPainted = false
            addActionListener { autoScroll = isSelected }
        }
        toolbar.add(asBtn)

        // Status
        statusLbl.font = statusLbl.font.deriveFont(10f)
        statusLbl.foreground = C_META
        statusLbl.border = JBUI.Borders.emptyLeft(8)
        toolbar.add(statusLbl)

        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    // ── Ingestion (called from background SSE thread) ─────────────────────────

    fun addLine(line: LogLine) {
        val parsed = ParsedLogLine.from(line)
        synchronized(allLines) {
            allLines.addLast(parsed)
            if (allLines.size > MAX_LINES) allLines.removeFirst()
        }
        if (matchesFilter(parsed)) pending.offer(parsed)
    }

    // ── EDT flush (every 50 ms) ───────────────────────────────────────────────

    private fun flushPending() {
        val batch = ArrayList<ParsedLogLine>(64)
        while (true) batch.add(pending.poll() ?: break)
        if (batch.isEmpty()) return

        // Remove oldest if over limit
        val overflow = model.size() + batch.size - MAX_LINES
        if (overflow > 0) {
            model.removeRange(0, minOf(overflow - 1, model.size() - 1))
        }
        batch.forEach { model.addElement(it) }

        statusLbl.text = "${model.size()} lines"
        if (autoScroll && model.size() > 0) {
            logList.ensureIndexIsVisible(model.size() - 1)
        }
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private fun matchesFilter(line: ParsedLogLine): Boolean {
        if (line.level !in activeLevels) return false
        if (tagFilter.isNotBlank()  && !line.tag.contains(tagFilter, ignoreCase = true) &&
            !line.source.contains(tagFilter, ignoreCase = true)) return false
        if (textFilter.isNotBlank() && !line.message.contains(textFilter, ignoreCase = true) &&
            !line.raw.contains(textFilter, ignoreCase = true)) return false
        return true
    }

    private fun refilter() {
        model.clear()
        pending.clear()
        val filtered = synchronized(allLines) { allLines.filter { matchesFilter(it) } }
        filtered.forEach { model.addElement(it) }
        statusLbl.text = "${model.size()} lines"
        if (autoScroll && model.size() > 0) logList.ensureIndexIsVisible(model.size() - 1)
    }

    private fun clearLogs() {
        synchronized(allLines) { allLines.clear() }
        pending.clear()
        model.clear()
        statusLbl.text = "0 lines"
    }

    // ── SSE connection ────────────────────────────────────────────────────────

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

    private fun minActiveLevel(): String {
        val order = listOf('V','D','I','W','E','F')
        return order.firstOrNull { it in activeLevels }?.toString() ?: "V"
    }

    private fun reconnectIfLevelChanged() {
        val newMin = minActiveLevel()
        if (newMin != lastMinLevel) { lastMinLevel = newMin; connect() }
    }

    fun reconnect() { connect() }

    fun dispose() {
        flushTimer.stop()
        logClient?.disconnect()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun label(text: String) = JLabel(text).also {
        it.foreground = C_META; it.font = it.font.deriveFont(11f)
    }

    private fun styleInput(f: JTextField) {
        f.font       = Font(Font.MONOSPACED, Font.PLAIN, 11)
        f.background = JBColor(Color(0x0d1117), Color(0x0d1117))
        f.foreground = JBColor.WHITE
        f.caretColor = JBColor.WHITE
        f.border     = JBUI.Borders.customLine(JBColor(Color(0x30363d), Color(0x30363d)))
    }

    private fun quickFilter(action: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = action()
        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = action()
        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {}
    }

    private fun MutableSet<Char>.set(key: Char, value: Boolean) {
        if (value) add(key) else remove(key)
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    inner class LogCellRenderer : ListCellRenderer<ParsedLogLine> {
        // Reuse one panel per renderer (Swing pattern)
        private val panel       = JPanel(null)   // null layout — manual setBounds
        private val tsLabel     = JLabel()
        private val pidLabel    = JLabel()
        private val tagLabel    = JLabel()
        private val levelLabel  = JLabel()
        private val msgLabel    = JLabel()
        private val stripe      = JPanel()

        // Widths (in unscaled px, scaled at paint time)
        private val TS_W    = JBUI.scale(155)
        private val PID_W   = JBUI.scale(90)
        private val TAG_W   = JBUI.scale(160)
        private val LV_W    = JBUI.scale(18)
        private val STRIPE  = JBUI.scale(3)
        private val PAD     = JBUI.scale(4)
        private val H       = JBUI.scale(18)

        private val monoFont = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f).toInt())

        init {
            panel.isOpaque = true
            stripe.isOpaque = true

            for (lbl in listOf(tsLabel, pidLabel, tagLabel, levelLabel, msgLabel)) {
                lbl.font       = monoFont
                lbl.isOpaque   = false
                panel.add(lbl)
            }
            panel.add(stripe)
        }

        override fun getListCellRendererComponent(
            list: JList<out ParsedLogLine>, value: ParsedLogLine,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val col = levelColor(value.level)
            panel.background = if (isSelected) BG_SEL else BG

            stripe.background = col
            stripe.setBounds(0, 0, STRIPE, H)

            var x = STRIPE + PAD

            tsLabel.text       = value.timestamp
            tsLabel.foreground = C_META
            tsLabel.setBounds(x, 0, TS_W, H)
            x += TS_W + PAD

            pidLabel.text       = value.pidTid
            pidLabel.foreground = C_META
            pidLabel.setBounds(x, 0, PID_W, H)
            x += PID_W + PAD

            val tag = value.tag.let { if (it.length > 23) it.take(22) + "…" else it.padEnd(23) }
            tagLabel.text       = tag
            tagLabel.foreground = C_META
            tagLabel.setBounds(x, 0, TAG_W, H)
            x += TAG_W + PAD

            levelLabel.text       = value.level.toString()
            levelLabel.foreground = col
            levelLabel.setBounds(x, 0, LV_W, H)
            x += LV_W + PAD

            msgLabel.text       = value.message
            msgLabel.foreground = if (isSelected) Color.WHITE else col
            msgLabel.setBounds(x, 0, list.width - x - PAD, H)

            panel.preferredSize = Dimension(list.width, H)
            return panel
        }
    }
}
