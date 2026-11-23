package com.noor.app
import android.Manifest
import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.*
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {
  lateinit var wv: WebView
  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS), 1)
    val am = getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
      startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
    wv = WebView(this); setContentView(wv)
    wv.settings.javaScriptEnabled = true; wv.settings.domStorageEnabled = true
    wv.settings.mediaPlaybackRequiresUserGesture = false
    wv.webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) = loader.shouldInterceptRequest(r.url)
    }
    wv.webChromeClient = object : WebChromeClient() {
      override fun onGeolocationPermissionsShowPrompt(o: String, cb: GeolocationPermissions.Callback) { cb.invoke(o, true, false) }
      override fun onPermissionRequest(r: PermissionRequest) { r.grant(r.resources) }
    }
    wv.addJavascriptInterface(Bridge(this), "Android")
    wv.loadUrl("https://appassets.androidplatform.net/assets/index.html")
  }
  override fun onBackPressed() { if (wv.canGoBack()) wv.goBack() else super.onBackPressed() }
}

class Bridge(val c: Context) {
  @JavascriptInterface fun schedule(json: String) {
    c.getSharedPreferences("a", 0).edit().putString("j", json).apply(); Alarms.setAll(c)
  }
}

object Alarms {
  fun setAll(c: Context) {
    val js = org.json.JSONArray(c.getSharedPreferences("a", 0).getString("j", "[]"))
    for (i in 0 until js.length()) { val o = js.getJSONObject(i); set(c, o.getInt("id"), o.getLong("ts"), o.getString("n"), o.getBoolean("a")) }
  }
  fun set(c: Context, id: Int, ts: Long, n: String, adhan: Boolean) {
    val am = c.getSystemService(AlarmManager::class.java)
    val it = Intent(c, AlarmReceiver::class.java).putExtra("id", id).putExtra("ts", ts).putExtra("n", n).putExtra("a", adhan)
    val pi = PendingIntent.getBroadcast(c, id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    var t = ts; while (t < System.currentTimeMillis()) t += 86400000L
    if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
  }
}

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent) {
    val id = i.getIntExtra("id", 0); val n = i.getStringExtra("n") ?: ""; val a = i.getBooleanExtra("a", true)
    val nm = c.getSystemService(NotificationManager::class.java); val ch = if (a) "adhan" else "rem"
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(ch) == null) {
      val chn = NotificationChannel(ch, if (a) "Adhan" else "Reminder", NotificationManager.IMPORTANCE_HIGH)
      val raw = c.resources.getIdentifier("adhan", "raw", c.packageName)
      if (a && raw != 0) chn.setSound(Uri.parse("android.resource://${c.packageName}/$raw"), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
      chn.enableVibration(true); nm.createNotificationChannel(chn)
    }
    val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, ch) else Notification.Builder(c)
    nm.notify(id, b.setContentTitle(n).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(pi).setAutoCancel(true).build())
    Alarms.set(c, id, i.getLongExtra("ts", 0) + 86400000L, n, a)
  }
}

class BootReceiver : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { Alarms.setAll(c) } }
