package com.example.deviceauth

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.LocaleList
import android.util.DisplayMetrics
import android.view.WindowManager
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone

class FingerprintCollector(private val context: Context) {

    fun collect(): String {
        val params = mutableListOf<String>()

        // ---------- 1. Аппаратные характеристики ----------
        val manufacturer = norm(Build.MANUFACTURER)
        val model = norm(Build.MODEL)
        val device = norm(Build.DEVICE)
        val product = norm(Build.PRODUCT)
        params.add("$manufacturer|$model|$device|$product")

        // ---------- 2. Системные параметры ----------
        val osVersion = norm(Build.VERSION.RELEASE)
        val sdkInt = Build.VERSION.SDK_INT
        val kernel = norm(System.getProperty("os.version") ?: "")
        val timeZone = norm(TimeZone.getDefault().id)

        // Языки
        val localeList = LocaleList.getDefault()
        val langList = mutableListOf<String>()
        for (i in 0 until localeList.size()) {
            langList.add(localeList.get(i).toLanguageTag())
        }
        val languages = langList.sorted().joinToString(",")
        val languagesNorm = norm(languages)

        params.add("Android|$osVersion|$sdkInt|$kernel|$timeZone|$languagesNorm")

        // ---------- 3. Экран ----------
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(dm)
        val screen = "${dm.widthPixels}x${dm.heightPixels}|${dm.densityDpi}|${dm.density}"
        params.add(norm(screen))

        // ---------- 4. Память ----------
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMemMB = memInfo.totalMem / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        params.add("$cores|$totalMemMB")

        // ---------- 5. ABI ----------
        val abis = Build.SUPPORTED_ABIS.map { norm(it) }.sorted().joinToString(",")
        params.add(abis)

        // ---------- 6. Датчики ----------
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            .map { "${it.type}|${norm(it.vendor)}|${norm(it.name)}" }
            .sorted()
            .joinToString(",")
        params.add(sensors)

        // ---------- 7. Камеры (фронтальная/задняя) ----------
        val pm = context.packageManager
        val hasFrontCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        val hasRearCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
        params.add("${if (hasFrontCamera) "1" else "0"}|${if (hasRearCamera) "1" else "0"}")

        val rawString = params.joinToString("|")
        return sha256(rawString)
    }

    private fun norm(s: String?): String =
        (s ?: "").trim().lowercase(Locale.ROOT)

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}