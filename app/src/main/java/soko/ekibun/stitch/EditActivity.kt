@file:Suppress("DEPRECATION")

package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class EditActivity : Activity() {
    companion object {
        const val REQUEST_IMPORT = 1
        const val REQUEST_SAVE = 2
    }

    private val editView by lazy { findViewById<EditView>(R.id.edit) }
    private val guidanceView by lazy { findViewById<View>(R.id.guidance) }
    private val selectInfo by lazy { findViewById<TextView>(R.id.select_info) }
    private val seekDx by lazy { findViewById<RangeSeekbar>(R.id.seek_x) }
    private val seekDy by lazy { findViewById<RangeSeekbar>(R.id.seek_y) }
    private val seekTrim by lazy { findViewById<RangeSeekbar>(R.id.seek_trim) }
    private val seekXRange by lazy { findViewById<RangeSeekbar>(R.id.seek_xrange) }
    private val seekYRange by lazy { findViewById<RangeSeekbar>(R.id.seek_yrange) }

    val selected by lazy {
        mutableSetOf<Int>()
    }

    private fun updateSelectInfo() {
        editView.dirty = true
        guidanceView.visibility = if (App.stitchInfo.isEmpty()) View.VISIBLE else View.INVISIBLE
        selectInfo.text = getString(R.string.label_select, selected.size, App.stitchInfo.size)
        editView.invalidate()
        val selected = App.stitchInfo.filterIndexed { i, it -> i > 0 && selected.contains(it.key) }
        if (selected.isNotEmpty()) {
            seekDx.a = selected.map { (it.dx.toFloat() / it.width + 1) / 2 }.average().toFloat()
            seekDy.a = selected.map { (it.dy.toFloat() / it.height + 1) / 2 }.average().toFloat()
            seekTrim.a = selected.map { it.a }.average().toFloat()
            seekTrim.b = selected.map { it.b }.average().toFloat()
            seekXRange.a = selected.map { it.xa }.average().toFloat()
            seekXRange.b = selected.map { it.xb }.average().toFloat()
            seekYRange.a = selected.map { it.ya }.average().toFloat()
            seekYRange.b = selected.map { it.yb }.average().toFloat()
            seekDx.invalidate()
            seekDy.invalidate()
            seekTrim.invalidate()
            seekXRange.invalidate()
            seekYRange.invalidate()
        }
    }

    private fun getVersion(context: Context): String {
        var versionName = ""
        var versionCode = 0
        var isApkInDebug = false
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = pi.versionName
            versionCode = pi.versionCode
            val info = context.applicationInfo
            isApkInDebug = info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return versionName + "-" + (if (isApkInDebug) "debug" else "release") + "(" + versionCode + ")"
    }

    private fun selectAll() {
        selected.clear()
        selected.addAll(App.stitchInfo.map { it.key })
        updateSelectInfo()
    }

    private fun selectClear() {
        selected.clear()
        updateSelectInfo()
    }

    fun selectToggle(info: Stitch.StitchInfo) {
        if (!selected.remove(info.key)) selected.add(info.key)
        updateSelectInfo()
    }

    private fun addImage(uri: Uri) {
        try {
            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
            val key = App.bitmapCache.saveBitmap(bitmap) ?: return
            val info = Stitch.StitchInfo(key, bitmap.width, bitmap.height)
            App.stitchInfo.add(info)
            selected.add(info.key)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateSystemUI() {
        if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION else 0)
        if (Build.VERSION.SDK_INT < 26) return
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        if (!night) window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val policyVersion = getString(R.string.policy_version)
        if (sp.getString("policy_version", "") != policyVersion) {
            val policyView = TextView(this)
            policyView.text = Html.fromHtml(getString(R.string.policy))
            val padding = (resources.displayMetrics.density * 16).roundToInt()
            policyView.setPaddingRelative(padding, padding, padding, 0)
            policyView.movementMethod = LinkMovementMethod.getInstance()
            AlertDialog.Builder(this).setCancelable(false).setView(policyView)
                .setPositiveButton(R.string.policy_accept) { _, _ ->
                    sp.edit().putString("policy_version", policyVersion).apply()
                }.setNegativeButton(R.string.policy_dismiss) { _, _ ->
                    finish()
                }.show()
        }

        setContentView(R.layout.activity_edit)

        window.decorView.setOnApplyWindowInsetsListener { _, windowInsets ->
            editView.setPadding(
                windowInsets.systemWindowInsetLeft,
                windowInsets.systemWindowInsetTop,
                windowInsets.systemWindowInsetRight,
                0
            )
            guidanceView.setPadding(
                windowInsets.systemWindowInsetLeft,
                windowInsets.systemWindowInsetTop,
                windowInsets.systemWindowInsetRight,
                0
            )
            findViewById<View>(R.id.panel0).setPadding(
                windowInsets.systemWindowInsetLeft,
                0,
                windowInsets.systemWindowInsetRight,
                0
            )
            findViewById<View>(R.id.panel1).setPadding(
                windowInsets.systemWindowInsetLeft,
                0,
                windowInsets.systemWindowInsetRight,
                0
            )
            findViewById<View>(R.id.panel2).setPadding(
                windowInsets.systemWindowInsetLeft,
                0,
                windowInsets.systemWindowInsetRight,
                windowInsets.systemWindowInsetBottom
            )
            windowInsets.consumeSystemWindowInsets()
        }

        findViewById<View>(R.id.menu_undo).setOnClickListener {
            App.undo()
            selected.clear()
            updateRange()
        }
        findViewById<View>(R.id.menu_import).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(Intent.createChooser(intent, "Stitch"), REQUEST_IMPORT)
        }
        findViewById<View>(R.id.menu_capture).setOnClickListener {
            this.startActivity(Intent(this, StartCaptureActivity::class.java))
        }
        findViewById<View>(R.id.menu_select_all).setOnClickListener {
            selectAll()
        }
        findViewById<View>(R.id.menu_select_clear).setOnClickListener {
            selectClear()
        }
        findViewById<View>(R.id.menu_swap).setOnClickListener {
            if (selected.size != 2) {
                Toast.makeText(this, R.string.please_select_swap, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            App.updateUndo()
            val (i, j) = selected.map { App.stitchInfo.indexOfFirst { info -> info.key == it } }
                .sorted()
            if (i < 0 || j < 0) return@setOnClickListener
            val a = App.stitchInfo[i]
            val b = App.stitchInfo.set(j, a)
            App.stitchInfo[i] = b
            val adx = a.dx
            val ady = a.dy
            a.dx = b.dx
            a.dy = b.dy
            b.dx = adx
            b.dy = ady
            updateRange()
        }
        findViewById<View>(R.id.menu_remove).setOnClickListener {
            if (selected.isEmpty()) {
                Toast.makeText(this, R.string.please_select_image, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.alert_delete, selected.size))
                .setPositiveButton(getString(R.string.alert_ok)) { _: DialogInterface, _: Int ->
                    App.updateUndo()
                    App.stitchInfo.removeAll { selected.contains(it.key) }
                    selected.clear()
                    updateRange()
                    updateSelectInfo()
                }.show()
        }
        findViewById<View>(R.id.menu_share).setOnClickListener {
            if (App.stitchInfo.isEmpty()) {
                Toast.makeText(this, R.string.please_add_image, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val progress = ProgressDialog.show(this, null, getString(R.string.alert_stitching))
            GlobalScope.launch(Dispatchers.IO) {
                val intent = try {
                    val bitmap = editView.drawToBitmap()
                    App.bitmapCache.saveToCache(bitmap)
                } catch (e: Throwable) {
                    e
                }
                runOnUiThread {
                    progress.cancel()
                    val ctx = this@EditActivity
                    if (intent is Intent) {
                        startActivity(intent)
                    } else {
                        AlertDialog.Builder(ctx)
                            .setTitle(R.string.throw_error)
                            .setMessage(Log.getStackTraceString(intent as? Throwable))
                            .setPositiveButton(getString(R.string.alert_ok)) { _, _ -> }
                            .show()
                    }
                }
            }
        }
        findViewById<View>(R.id.menu_save).setOnClickListener {
            if (App.stitchInfo.isEmpty()) {
                Toast.makeText(this, R.string.please_add_image, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            saveIntent.type = "image/png"
            saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
            saveIntent.putExtra(Intent.EXTRA_TITLE, App.bitmapCache.createFileName())
            startActivityForResult(saveIntent, REQUEST_SAVE)
        }
        findViewById<View>(R.id.menu_auto_stitch).setOnClickListener {
            if (selected.isEmpty()) {
                Toast.makeText(this, R.string.please_select_image, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val progress = ProgressDialog(this)
            progress.setMessage(getString(R.string.alert_computing))
            progress.show()
            GlobalScope.launch(Dispatchers.IO) {
                App.updateUndo()
                App.stitchInfo.reduceOrNull { acc, it ->
                    if (progress.isShowing && selected.contains(it.key)) {
                        val img0 = App.bitmapCache.getBitmap(acc.image)
                        val img1 = App.bitmapCache.getBitmap(it.image)
                        if (img0 != null && img1 != null) {
                            val data = DoubleArray(9)
                            if (Stitch.combineNative(img0, img1, data) && progress.isShowing) {
                                it.dx = data[2].roundToInt()
                                it.dy = data[5].roundToInt()
                            }
                        }
                    }
                    it
                }
                runOnUiThread {
                    updateRange()
                    progress.cancel()
                }
            }
        }
        val str = getString(R.string.guidance_info, getVersion(this))
        findViewById<TextView>(R.id.guidance_info).text = Html.fromHtml(str)
        findViewById<TextView>(R.id.menu_terms).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://ekibun.github.io/Stitch/$policyVersion/terms")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.open_error, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.menu_privacy).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://ekibun.github.io/Stitch/$policyVersion/privacy")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.open_error, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.menu_opensource).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.menu_opensource)
                .setMessage(Html.fromHtml(getString(R.string.opensource)))
                .setPositiveButton(getString(R.string.alert_ok)) { _, _ -> }
                .show()
        }
        findViewById<TextView>(R.id.menu_support).setOnClickListener {
            val intentFullUrl = "intent://platformapi/startapp?saId=10000007&" +
                    "qrcode=https%3A%2F%2Fqr.alipay.com%2Ffkx14754b1r4mkbh6gfgg24#Intent;" +
                    "scheme=alipayqr;package=com.eg.android.AlipayGphone;end"
            try {
                val intent = Intent.parseUri(intentFullUrl, Intent.URI_INTENT_SCHEME)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.support_error, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.menu_github).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://github.com/ekibun/Stitch")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.open_error, Toast.LENGTH_SHORT).show()
            }
        }

        seekDx.type = RangeSeekbar.TYPE_CENTER
        seekDx.a = 0.5f
        seekDx.onRangeChange = { a, _ ->
            App.stitchInfo.forEach {
                if (selected.contains(it.key)) it.dx = ((a * 2 - 1) * it.width).roundToInt()
            }
            updateRange()
        }
        seekDy.type = RangeSeekbar.TYPE_CENTER
        seekDy.a = 0.5f
        seekDy.onRangeChange = { a, _ ->
            App.stitchInfo.forEach {
                if (selected.contains(it.key)) it.dy = ((a * 2 - 1) * it.height).roundToInt()
            }
            updateRange()
        }
        seekTrim.type = RangeSeekbar.TYPE_GRADIENT
        seekTrim.a = 0.4f
        seekTrim.b = 0.6f
        seekTrim.onRangeChange = { a, b ->
            App.stitchInfo.forEach {
                if (selected.contains(it.key)) {
                    it.a = a
                    it.b = b
                }
            }
            updateRange()
        }
        seekXRange.onRangeChange = { a, b ->
            App.stitchInfo.forEach {
                if (selected.contains(it.key)) {
                    it.xa = a
                    it.xb = b
                }
            }
            updateRange()
        }
        seekYRange.onRangeChange = { a, b ->
            App.stitchInfo.forEach {
                if (selected.contains(it.key)) {
                    it.ya = a
                    it.yb = b
                }
            }
            updateRange()
        }
        selectAll()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQUEST_IMPORT -> {
                App.updateUndo()
                selected.clear()
                val progress = ProgressDialog.show(
                    this, null,
                    getString(R.string.alert_reading)
                )
                GlobalScope.launch(Dispatchers.IO) {
                    val clipData = data?.clipData
                    if (clipData != null) {
                        val count: Int =
                            clipData.itemCount
                        for (i in 0 until count) {
                            addImage(clipData.getItemAt(i).uri)
                        }
                    } else data?.data?.let { path ->
                        addImage(path)
                    }
                    runOnUiThread {
                        progress.cancel()
                        updateRange()
                    }
                }
            }
            REQUEST_SAVE -> {
                val progress = ProgressDialog.show(
                    this, null,
                    getString(R.string.alert_stitching)
                )
                GlobalScope.launch(Dispatchers.IO) {
                    val err = try {
                        val fileOutputStream = contentResolver.openOutputStream(data?.data!!)!!
                        val bitmap = editView.drawToBitmap()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                        fileOutputStream.close()
                    } catch (e: Throwable) {
                        e
                    }
                    runOnUiThread {
                        progress.cancel()
                        val ctx = this@EditActivity
                        if (err !is Throwable) {
                            Toast.makeText(
                                ctx,
                                R.string.save_success,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            AlertDialog.Builder(ctx)
                                .setTitle(R.string.throw_error)
                                .setMessage(Log.getStackTraceString(err))
                                .setPositiveButton(getString(R.string.alert_ok)) { _, _ -> }
                                .show()
                        }
                    }
                }
            }
        }
    }

    fun updateRange() {
        editView.update()
        updateSelectInfo()
    }

    override fun onResume() {
        super.onResume()
        updateRange()
        updateSystemUI()
    }

    override fun onBackPressed() {
        if (selected.isEmpty())
            super.onBackPressed()
        else selectClear()
    }
}