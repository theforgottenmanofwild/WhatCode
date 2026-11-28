package com.whatcode.bench

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.whatcode.editor.CodeEditText
import com.whatcode.editor.EditorColouring
import com.whatcode.editor.Highlighter
import com.whatcode.editor.SyntaxScheme
import com.whatcode.settings.EditorSettings
import java.io.File

/**
 * Measures the real editor on generated files: how long a file takes to open, how long a key press takes to show,
 * how smoothly the text scrolls, and how much memory it all takes. Debug builds only. Pick a kind and a size, tap
 * **Run**, and copy the report. Each run is one file; a file that does not fit in memory is reported, not crashed.
 *
 * The editor in the middle is the app's own [CodeEditText] with the same colouring after a pause in typing as the
 * editor screen, so the numbers are the ones a person gets.
 */
class BenchActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var editor: CodeEditText
    private lateinit var colouring: EditorColouring
    private lateinit var report: TextView
    private lateinit var run: Button
    private var kind = BenchData.Kind.SOURCE
    private var sizeMb = 1
    private val log = StringBuilder()
    private var running = false

    /** The longest the Java heap, the native heap and the whole process got during a run, in bytes. */
    @Volatile private var peakJava = 0L
    @Volatile private var peakNative = 0L
    @Volatile private var peakPss = 0L
    @Volatile private var sampling = false

    private val scheme = SyntaxScheme.of(light = false, editorBackground = Color.BLACK)
    private val highlightMs = ArrayList<Long>()

    private val highlightTask = Runnable { highlight(null) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(8), dp(28), dp(8), dp(8))
        }
        root.addView(row(BenchData.Kind.values().map { k -> k.label to { kind = k } }))
        root.addView(row(listOf(1, 5, 20, 100).map { mb -> "$mb MB" to { sizeMb = mb } }))
        run = Button(this).apply {
            text = "Run"
            isAllCaps = false
            setOnClickListener { start() }
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(run, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(
            Button(this).apply {
                text = "Copy report"
                isAllCaps = false
                setOnClickListener {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("bench", log.toString()))
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(buttons)

        editor = CodeEditText(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            wordWrap = EditorSettings.DEFAULT_WORD_WRAP
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // The same wait as the editor screen.
                    ui.removeCallbacks(highlightTask)
                    ui.postDelayed(highlightTask, 200)
                }
            })
        }
        colouring = EditorColouring(editor)
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        report = TextView(this).apply {
            setTextColor(Color.rgb(200, 255, 200))
            typeface = Typeface.MONOSPACE
            textSize = 11f
        }
        val scroll = ScrollView(this).apply { addView(report) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)))
        setContentView(root)
        say(deviceLine())
    }

    private fun row(items: List<Pair<String, () -> Unit>>): LinearLayout {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val all = ArrayList<Button>()
        for ((label, pick) in items) {
            val b = Button(this).apply { text = label; isAllCaps = false; textSize = 11f }
            all.add(b)
            b.setOnClickListener {
                pick()
                all.forEach { it.alpha = if (it === b) 1f else 0.5f }
            }
            line.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        all.forEachIndexed { i, b -> b.alpha = if (i == 0) 1f else 0.5f }
        return line
    }

    private fun deviceLine(): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT}), heap limit " +
            "${Runtime.getRuntime().maxMemory() shr 20} MB, memory class ${am.memoryClass} MB (large ${am.largeMemoryClass} MB)"
    }

    private fun say(line: String) {
        log.append(line).append('\n')
        report.text = log.toString()
        (report.parent as? ScrollView)?.post { (report.parent as ScrollView).fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ------------------------------------------------------------------ one run

    private fun start() {
        if (running) return
        running = true
        run.isEnabled = false
        log.setLength(0)
        say(deviceLine())
        say("== ${kind.label}, $sizeMb MB ==")
        editor.setTextNoHistory("")
        System.gc()
        peakJava = 0; peakNative = 0; peakPss = 0
        startSampling()
        val file = File(cacheDir, "bench." + kind.extension)
        Thread {
            try {
                file.outputStream().buffered().use { BenchData.write(kind, sizeMb.toLong() shl 20, it) }
                val t = SystemClock.elapsedRealtime()
                val text = file.readText(Charsets.UTF_8)
                val readMs = SystemClock.elapsedRealtime() - t
                ui.post { open(text, readMs, file) }
            } catch (e: OutOfMemoryError) {
                ui.post { finish("out of memory while reading the file", file) }
            } catch (e: Exception) {
                ui.post { finish("failed: ${e.message}", file) }
            }
        }.start()
    }

    private fun open(text: String, readMs: Long, file: File) {
        say("read the file: $readMs ms (${text.length} chars)")
        val lang = Highlighter.languageFor(file.name)
        editor.lang = lang
        val t0 = SystemClock.elapsedRealtime()
        try {
            editor.setTextNoHistory(text)
        } catch (e: OutOfMemoryError) {
            finish("out of memory while putting the text in the editor", file)
            return
        }
        val setMs = SystemClock.elapsedRealtime() - t0
        afterNextDraw {
            val shownMs = SystemClock.elapsedRealtime() - t0
            say("open: setText $setMs ms, first frame $shownMs ms")
            highlight { colourMs ->
                say("colours on the text: $colourMs ms${if (text.length > Highlighter.MAX_CHARS) " (none: file is over ${Highlighter.MAX_CHARS} chars)" else ""}")
                keystrokes(file)
            }
        }
    }

    /** Colours the text as the editor screen does, and tells how long it took until the colours were on. */
    private fun highlight(done: ((Long) -> Unit)?) {
        val t = SystemClock.elapsedRealtime()
        colouring.apply(editor.lang, scheme) {
            val ms = SystemClock.elapsedRealtime() - t
            highlightMs.add(ms)
            done?.invoke(ms)
        }
    }

    private fun keystrokes(file: File) {
        highlightMs.clear()
        val latencies = ArrayList<Long>()
        val total = 30
        var i = 0
        fun next() {
            if (i == total) {
                // Let the last colouring finish before the next measurement.
                ui.postDelayed({
                    report("key press to the screen", latencies)
                    if (highlightMs.isNotEmpty()) report("colouring after a pause", highlightMs)
                    scrolling(file)
                }, 1500)
                return
            }
            i++
            val at = (editor.text.length / 2 + i).coerceAtMost(editor.text.length)
            val t = SystemClock.elapsedRealtime()
            try {
                editor.text.insert(at, "x")
            } catch (e: OutOfMemoryError) {
                finish("out of memory while typing", file)
                return
            }
            afterNextDraw {
                latencies.add(SystemClock.elapsedRealtime() - t)
                ui.postDelayed({ next() }, 80)
            }
        }
        next()
    }

    private fun scrolling(file: File) {
        val frames = ArrayList<Long>()
        var last = 0L
        var count = 0
        val step = dp(48)
        editor.scrollTo(0, 0)
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (last != 0L) frames.add((frameTimeNanos - last) / 1_000_000)
                last = frameTimeNanos
                // Down for 150 frames, then back up for 150.
                editor.scrollBy(0, if (count < 150) step else -step)
                if (++count < 300) {
                    Choreographer.getInstance().postFrameCallback(this)
                } else {
                    report("frame time while scrolling (ms between frames)", frames)
                    val janky = frames.count { it > 20 }
                    say("frames over 20 ms: $janky of ${frames.size}")
                    finish(null, file)
                }
            }
        }
        Choreographer.getInstance().postFrameCallback(callback)
    }

    private fun report(what: String, values: List<Long>) {
        if (values.isEmpty()) return
        val s = values.sorted()
        say("$what: median ${s[s.size / 2]}, 95th ${s[(s.size * 95 / 100).coerceAtMost(s.size - 1)]}, worst ${s.last()} ms (${s.size} samples)")
    }

    private fun finish(problem: String?, file: File) {
        if (problem != null) say(problem)
        sampling = false
        say("memory peak: Java ${peakJava shr 20} MB, native ${peakNative shr 20} MB, whole app ${peakPss shr 20} MB")
        file.delete()
        running = false
        run.isEnabled = true
    }

    // ------------------------------------------------------------------ helpers

    /** Runs [block] once, after the editor has been drawn next. */
    private fun afterNextDraw(block: () -> Unit) {
        val observer = editor.viewTreeObserver
        observer.addOnDrawListener(object : android.view.ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                // A listener can not be removed from inside its own call, so it goes on the next turn.
                ui.post {
                    editor.viewTreeObserver.removeOnDrawListener(this)
                    block()
                }
            }
        })
        editor.invalidate()
    }

    private fun startSampling() {
        sampling = true
        Thread {
            while (sampling) {
                val rt = Runtime.getRuntime()
                peakJava = maxOf(peakJava, rt.totalMemory() - rt.freeMemory())
                peakNative = maxOf(peakNative, Debug.getNativeHeapAllocatedSize())
                peakPss = maxOf(peakPss, Debug.getPss() * 1024)
                SystemClock.sleep(100)
            }
        }.start()
    }
}
