package com.ramim.homedragon

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.util.Log
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Simple dark home screen for the app: a big start/stop button, three percentage sliders
 * (quality, dragon size, dragon speed) that change the dragon live, and three setup rows.
 */
class MainActivity : Activity() {

    private companion object {
        val BG = Color.parseColor("#0A0F1E")
        val CARD = Color.parseColor("#121A30")
        val STROKE = Color.parseColor("#223152")
        val TRACK = Color.parseColor("#26365A")
        val FG = Color.parseColor("#EAF2FF")
        val MUTED = Color.parseColor("#8A9BBD")
        val TEAL = Color.parseColor("#2DD4BF")
        val BLUE = Color.parseColor("#3B82F6")
        val ORANGE = Color.parseColor("#FF9A3C")
        val RED = Color.parseColor("#F0634F")
        val GREEN = Color.parseColor("#3DDC97")
    }

    private lateinit var pill: TextView
    private lateinit var toggle: TextView
    private val setupRows = ArrayList<SetupRow>()
    private val sliders = ArrayList<Slider>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(fill: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun gradient(a: Int, b: Int, radius: Int) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a, b)).apply {
            cornerRadius = dp(radius).toFloat()
        }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(CARD, 20, STROKE)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(16) }
    }

    // ---------------------------------------------------------------- slider row

    private inner class Slider(
        title: String, hint: String, val min: Int, val max: Int, start: Int,
        private val save: (Int) -> Unit, private val live: () -> Unit, private val step: Int = 1
    ) {
        val value = text("$start%", 16f, TEAL, true)
        val seek = SeekBar(this@MainActivity)
        val view = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }

        init {
            val head = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(text(title, 16f, FG, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(value)
            view.addView(head)

            seek.max = (max - min) / step
            try {
                val track = GradientDrawable().apply { setColor(TRACK); cornerRadius = dp(4).toFloat(); setSize(0, dp(8)) }
                val fill = ClipDrawable(
                    gradient(TEAL, BLUE, 4).apply { setSize(0, dp(8)) }, Gravity.START, ClipDrawable.HORIZONTAL
                )
                val layers = LayerDrawable(arrayOf<android.graphics.drawable.Drawable>(track, fill))
                layers.setId(0, android.R.id.background)
                layers.setId(1, android.R.id.progress)
                val knob = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setStroke(dp(4), TEAL)
                    setSize(dp(26), dp(26))
                }
                seek.progressDrawable = layers
                seek.thumb = knob
                seek.setPadding(dp(13), dp(10), dp(13), dp(10))
            } catch (_: Throwable) {
                // stock slider look is fine as a fallback
            }
            seek.progress = ((start - min + step / 2) / step).coerceIn(0, (max - min) / step)
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    value.text = "${p * step + min}%"
                    if (fromUser) { save(p * step + min); live() }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
            view.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) })
            view.addView(text(hint, 12f, MUTED))
        }

        fun reset() { seek.progress = (100 - min) / step; save(100); live() }
    }

    // ---------------------------------------------------------------- setup row

    private inner class SetupRow(title: String, desc: String, private val onClick: () -> Unit) {
        val state = text("", 13f, MUTED, true)
        val view = LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            setOnClickListener { onClick() }
        }

        init {
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(title, 15f, FG, true))
            col.addView(text(desc, 12f, MUTED))
            view.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            view.addView(state)
        }

        fun update(ok: Boolean) {
            state.text = if (ok) "On" else "Set up  ›"
            state.setTextColor(if (ok) GREEN else ORANGE)
        }
    }

    // ---------------------------------------------------------------- screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the app crashed last time, show the error instead of the normal screen (App.kt saved it).
        val saved = File(filesDir, "crash.txt")
        if (saved.exists()) {
            val trace = try { saved.readText() } catch (_: Throwable) { "unreadable crash file" }
            try { saved.delete() } catch (_: Throwable) {}
            showError("The app crashed last time:", trace)
            return
        }
        try {
            buildUi()
        } catch (t: Throwable) {
            showError("The screen could not be built:", Log.getStackTraceString(t))
        }
    }

    /** Plain, dependency-free error page so a crash can be read and screenshotted. */
    private fun showError(title: String, trace: String) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#0A0F1E"))
        }
        col.addView(TextView(this).apply {
            text = "Home Dragon\n$title"
            textSize = 18f
            setTextColor(Color.WHITE)
        })
        col.addView(TextView(this).apply {
            text = trace.take(3500)
            textSize = 11f
            setTextColor(Color.parseColor("#FFB4A8"))
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, dp(12))
        })
        col.addView(TextView(this).apply {
            text = "Open the app anyway"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#2B6CFF"))
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { recreate() }
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0A0F1E")); addView(col) })
    }

    @Suppress("DEPRECATION")
    private fun buildUi() {
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        // header
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val badge = TextView(this).apply {
            text = "🐉"
            textSize = 28f
            gravity = Gravity.CENTER
            background = gradient(TEAL, BLUE, 18)
        }
        header.addView(badge, LinearLayout.LayoutParams(dp(56), dp(56)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        titles.addView(text("Home Dragon", 26f, FG, true))
        titles.addView(text("A live pet dragon for your home screen", 13f, MUTED))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(header)

        // status pill + big button
        pill = text("", 13f, MUTED, true).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        col.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(22) })

        toggle = text("", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { toggleService() }
        }
        col.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))
            .apply { topMargin = dp(12) })

        // sliders
        val settings = card()
        settings.addView(text("Dragon settings", 13f, MUTED, true))
        fun gap() = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) }

        val quality = Slider(
            "Quality", "Steps of 10%. 100% = full screen refresh rate (120 fps) in every state; each step down lowers the frame rate by 10% and thins the fire and smoke. Sitting completely still drops to 30 fps. Lower = less battery use.",
            10, 100, Prefs.qualityPct(this), { Prefs.setQualityPct(this, it) }, { DragonService.instance?.view?.reloadSettings() }, step = 10
        )
        val size = Slider(
            "Dragon size", "How big the dragon is on your icons.",
            50, 150, Prefs.scalePct(this), { Prefs.setScalePct(this, it) }, { DragonService.instance?.view?.reloadPrefs() }
        )
        val speed = Slider(
            "Dragon speed", "How fast it flies, walks and breathes fire.",
            50, 150, Prefs.speedPct(this), { Prefs.setSpeedPct(this, it) }, { DragonService.instance?.view?.reloadSettings() }
        )
        sliders.addAll(listOf(quality, size, speed))
        settings.addView(gap()); settings.addView(quality.view)
        settings.addView(gap()); settings.addView(size.view)
        settings.addView(gap()); settings.addView(speed.view)
        val reset = text("Reset all to 100%", 14f, ORANGE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(2))
            isClickable = true
            setOnClickListener { sliders.forEach { it.reset() } }
        }
        settings.addView(reset)
        col.addView(settings)

        // setup rows
        val setup = card()
        setup.addView(text("Setup", 13f, MUTED, true))
        val r1 = SetupRow("Draw over other apps", "Lets the dragon appear on top of your home screen") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val r2 = SetupRow("Icon finder", "Lets the dragon see where your icons are") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Open Home Dragon icon finder and switch it on.", Toast.LENGTH_LONG).show()
        }
        val r3 = SetupRow("Background running", "Keeps the dragon alive when the screen is off") { openBackgroundSettings() }
        setupRows.addAll(listOf(r1, r2, r3))
        setup.addView(r1.view); setup.addView(r2.view); setup.addView(r3.view)
        col.addView(setup)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            addView(col)
            // Android 15 draws apps edge to edge: keep the content clear of the status and navigation bars
            setOnApplyWindowInsetsListener { v, ins ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val b = ins.getInsets(WindowInsets.Type.systemBars())
                    v.setPadding(b.left, b.top, b.right, b.bottom)
                } else {
                    v.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
                }
                ins
            }
        }
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                window.insetsController?.setSystemBarsAppearance(
                    0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } catch (_: Throwable) {
            }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (setupRows.size == 3) refresh()
        } catch (t: Throwable) {
            showError("The status refresh failed:", Log.getStackTraceString(t))
        }
    }

    private fun a11yEnabled(): Boolean {
        val me = ComponentName(this, IconFinderService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        setupRows[0].update(overlay)
        setupRows[1].update(a11yEnabled())
        setupRows[2].update(pm.isIgnoringBatteryOptimizations(packageName))

        val running = DragonService.instance != null
        pill.text = if (running) "●  Running" else "○  Stopped"
        pill.setTextColor(if (running) GREEN else MUTED)
        pill.background = shape(if (running) Color.parseColor("#12332A") else CARD, 20, if (running) GREEN else STROKE)
        toggle.text = if (running) "Stop dragon" else "Start dragon"
        toggle.background = if (running) gradient(Color.parseColor("#E5533D"), Color.parseColor("#F08A3C"), 18)
        else gradient(TEAL, BLUE, 18)
    }

    private fun toggleService() {
        if (DragonService.instance != null) {
            startService(Intent(this, DragonService::class.java).setAction(DragonService.ACTION_STOP))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow drawing over other apps first.", Toast.LENGTH_SHORT).show()
                return
            }
            startForegroundService(Intent(this, DragonService::class.java))
        }
        window.decorView.postDelayed({ refresh() }, 500)
    }

    private fun openBackgroundSettings() {
        // Battery: ask for "No restrictions".
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        // HyperOS / MIUI: Autostart screen (not available on every build, so failure is fine).
        try {
            startActivity(Intent().setComponent(ComponentName(
                "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )))
        } catch (_: Exception) {
        }
        Toast.makeText(this, "Also lock Home Dragon in the recent apps list.", Toast.LENGTH_LONG).show()
    }
}
