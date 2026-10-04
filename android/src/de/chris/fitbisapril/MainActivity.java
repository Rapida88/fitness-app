package de.chris.fitbisapril;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long downloadId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        applyBars(systemDark());
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri u = Uri.parse(url);
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                openPhotoChooser();
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
        checkForUpdate(false);
    }

    private boolean systemDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyBars(boolean dark) {
        Window w = getWindow();
        int bg = dark ? Color.parseColor("#0C0F16") : Color.parseColor("#F2F4F8");
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        w.getDecorView().setSystemUiVisibility(dark ? 0 : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | 0x10 /* LIGHT_NAVIGATION_BAR */));
        if (web != null) web.setBackgroundColor(bg);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (web != null) web.evaluateJavascript("window.__fitSysTheme&&window.__fitSysTheme()", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.__fitResume&&window.__fitResume()", null);
    }

    /* ---------- Schritte über Health Connect (Mi Band via Mi Fitness) ---------- */

    private static final int REQ_STEPS = 77;

    private String stepsState() {
        if (Build.VERSION.SDK_INT < 34) return "old_android";
        return checkSelfPermission(Steps.PERM) == android.content.pm.PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_STEPS && web != null) {
            web.evaluateJavascript("window.__fitStepsPerm&&window.__fitStepsPerm(" + JSONObject.quote(stepsState()) + ")", null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    /* ---------- Kamera / Galerie ---------- */

    private void openPhotoChooser() {
        Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        gallery.setType("image/*");

        Intent chooser = Intent.createChooser(gallery, "Foto vom Essen");
        cameraUri = null;
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "essen_" + System.currentTimeMillis() + ".jpg");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cv.put("relative_path", Environment.DIRECTORY_PICTURES + "/FitBisApril");
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (cameraUri != null) {
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
            }
        } catch (Exception e) {
            cameraUri = null;
        }
        try {
            startActivityForResult(chooser, REQ_FILE);
        } catch (Exception e) {
            finishChooser(null);
        }
    }

    private void finishChooser(Uri[] result) {
        if (fileCallback != null) fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE) return;
        Uri picked = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                picked = data.getData();
            } else if (cameraUri != null && hasContent(cameraUri)) {
                picked = cameraUri;
            }
        }
        if (cameraUri != null && (picked == null || !picked.equals(cameraUri))) {
            try { getContentResolver().delete(cameraUri, null, null); } catch (Exception e) { }
        }
        finishChooser(picked != null ? new Uri[]{picked} : null);
    }

    private boolean hasContent(Uri u) {
        try {
            InputStream in = getContentResolver().openInputStream(u);
            if (in == null) return false;
            int b = in.read();
            in.close();
            return b != -1;
        } catch (Exception e) {
            return false;
        }
    }

    /* ---------- JS-Brücke ---------- */

    private void callJs(final String id, final boolean ok, final String text) {
        main.post(new Runnable() {
            @Override public void run() {
                web.evaluateJavascript("window.__fitCb(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(text) + ")", null);
            }
        });
    }

    private class Bridge {
        @JavascriptInterface
        public String versionName() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "?";
            }
        }

        @JavascriptInterface
        public boolean isDark() {
            return systemDark();
        }

        @JavascriptInterface
        public void setBars(final boolean dark) {
            main.post(new Runnable() {
                @Override public void run() { applyBars(dark); }
            });
        }

        @JavascriptInterface
        public void checkUpdate(boolean manual) {
            checkForUpdate(manual);
        }

        @JavascriptInterface
        public void share(String filename, String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, filename);
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Daten sichern"));
        }

        @JavascriptInterface
        public String stepsStatus() {
            return stepsState();
        }

        @JavascriptInterface
        public void stepsConnect() {
            main.post(new Runnable() {
                @Override public void run() {
                    if (Build.VERSION.SDK_INT < 34) return;
                    requestPermissions(new String[] { Steps.PERM }, REQ_STEPS);
                }
            });
        }

        @JavascriptInterface
        public void stepsSettings() {
            main.post(new Runnable() {
                @Override public void run() {
                    try {
                        Intent i = new Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS");
                        i.putExtra("android.intent.extra.PACKAGE_NAME", getPackageName());
                        startActivity(i);
                    } catch (Exception e) {
                        try { startActivity(new Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")); }
                        catch (Exception e2) { Toast.makeText(MainActivity.this, "Health Connect nicht gefunden", Toast.LENGTH_LONG).show(); }
                    }
                }
            });
        }

        @JavascriptInterface
        public void stepsRead(final int days, final String id) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (Build.VERSION.SDK_INT < 34) { callJs(id, false, "old_android"); return; }
                    try {
                        Steps.read(MainActivity.this, Math.max(1, Math.min(14, days)), new Steps.Cb() {
                            @Override public void done(boolean ok, String text) { callJs(id, ok, text); }
                        });
                    } catch (Throwable t) {
                        callJs(id, false, "hc_" + t.getClass().getSimpleName());
                    }
                }
            });
        }

        @JavascriptInterface
        public void gemini(final String b64, final String prompt, final String key, final String model, final String id) {
            new Thread(new Runnable() {
                @Override public void run() { runGemini(b64, prompt, key, model, id); }
            }).start();
        }
    }

    /* ---------- Fotos und Rezepte über die Gemini API ---------- */

    private void runGemini(String b64, String prompt, String key, String model, String id) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String err = runGeminiOnce(b64, prompt, key, model, id);
            if (err == null) return;
            boolean retry = err.startsWith("api_5") || err.startsWith("offline");
            if (!retry || attempt == 2) { callJs(id, false, err); return; }
            try { Thread.sleep(2000L * (attempt + 1)); } catch (InterruptedException ie) { callJs(id, false, err); return; }
        }
    }

    private static String apiMessage(String resp) {
        try {
            JSONObject e = new JSONObject(resp).optJSONObject("error");
            String m = e != null ? e.optString("message", "") : "";
            m = m.replaceAll("\\s+", " ").trim();
            return m.length() > 120 ? m.substring(0, 120) : m;
        } catch (Exception ex) { return ""; }
    }

    /** Liefert null bei Erfolg (Antwort bereits an JS übergeben), sonst einen Fehlercode. */
    private String runGeminiOnce(String b64, String prompt, String key, String model, String id) {
        HttpURLConnection c = null;
        try {
            JSONArray parts = new JSONArray();
            if (b64 != null && !b64.isEmpty()) {
                parts.put(new JSONObject().put("inline_data",
                    new JSONObject().put("mime_type", "image/jpeg").put("data", b64)));
            }
            parts.put(new JSONObject().put("text", prompt));
            JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", new JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.4));

            String m = model.replaceAll("[^A-Za-z0-9._-]", "");
            c = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent").openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("content-type", "application/json");
            c.setRequestProperty("x-goog-api-key", key);
            OutputStream out = c.getOutputStream();
            out.write(body.toString().getBytes("UTF-8"));
            out.close();

            int code = c.getResponseCode();
            String resp = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code == 400 && resp.contains("API key")) return "bad_key";
            if (code == 401 || code == 403) return "bad_key";
            if (code == 404) return "model";
            if (code == 429) return "quota|" + apiMessage(resp);
            if (code >= 400) return "api_" + code + "|" + apiMessage(resp);

            JSONObject r = new JSONObject(resp);
            JSONObject fb = r.optJSONObject("promptFeedback");
            if (fb != null && fb.has("blockReason")) return "refused";
            JSONArray cands = r.optJSONArray("candidates");
            StringBuilder sb = new StringBuilder();
            String finish = "";
            if (cands != null && cands.length() > 0) {
                finish = cands.getJSONObject(0).optString("finishReason", "");
                JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                JSONArray ps = content != null ? content.optJSONArray("parts") : null;
                if (ps != null) {
                    for (int i = 0; i < ps.length(); i++) {
                        JSONObject pt = ps.optJSONObject(i);
                        if (pt != null && !pt.optBoolean("thought", false)) sb.append(pt.optString("text", ""));
                    }
                }
            }
            if ("MAX_TOKENS".equals(finish)) return "too_long";
            if (sb.length() == 0) return "SAFETY".equals(finish) ? "refused" : "parse|leer " + finish;
            callJs(id, true, sb.toString());
            return null;
        } catch (java.io.IOException e) {
            return "offline|" + e.getClass().getSimpleName();
        } catch (Exception e) {
            return "parse|" + e.getClass().getSimpleName();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    /* ---------- Updates ---------- */

    private void checkForUpdate(final boolean manual) {
        if (!manual) {
            SharedPreferences p = getSharedPreferences("upd", MODE_PRIVATE);
            long last = p.getLong("last", 0);
            if (System.currentTimeMillis() - last < 6L * 3600 * 1000) return;
            p.edit().putLong("last", System.currentTimeMillis()).apply();
        } else {
            toast("Suche nach Updates…");
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(getString(R.string.update_url)).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    c.setInstanceFollowRedirects(true);
                    int code = c.getResponseCode();
                    if (code != 200) { if (manual) toast("Keine Updates gefunden."); return; }
                    JSONObject v = new JSONObject(readAll(c.getInputStream()));
                    final int remote = v.getInt("versionCode");
                    final String name = v.optString("versionName", "");
                    final String apk = v.getString("apkUrl");
                    final String notes = v.optString("notes", "");
                    PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                    if (remote <= pi.versionCode) { if (manual) toast("Du hast die neueste Version."); return; }
                    main.post(new Runnable() {
                        @Override public void run() { askUpdate(name, notes, apk); }
                    });
                } catch (Exception e) {
                    if (manual) toast("Update-Prüfung fehlgeschlagen. Bist du online?");
                }
            }
        }).start();
    }

    private void askUpdate(String name, String notes, final String apkUrl) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
            .setTitle("Update verfügbar" + (name.isEmpty() ? "" : ": Version " + name))
            .setMessage(notes.isEmpty() ? "Eine neue Version der App ist da. Jetzt herunterladen und installieren?" : notes + "\n\nJetzt herunterladen und installieren?")
            .setPositiveButton("Installieren", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { startDownload(apkUrl); }
            })
            .setNegativeButton("Später", null)
            .show();
    }

    private boolean canInstall() {
        try {
            return (Boolean) getPackageManager().getClass().getMethod("canRequestPackageInstalls").invoke(getPackageManager());
        } catch (Exception e) {
            return true;
        }
    }

    private void startDownload(String apkUrl) {
        if (!canInstall()) {
            toast("Erlaube der App einmal, Updates zu installieren, und tippe dann nochmal auf Installieren.");
            try {
                startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())));
            } catch (Exception e) { }
            return;
        }
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(apkUrl));
            r.setTitle("Fit bis April – Update");
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "fit-bis-april-update-" + System.currentTimeMillis() + ".apk");
            downloadId = dm.enqueue(r);
            toast("Update wird geladen…");
            pollDownload(dm);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)));
        }
    }

    private void pollDownload(final DownloadManager dm) {
        main.postDelayed(new Runnable() {
            @Override public void run() {
                Cursor c = dm.query(new DownloadManager.Query().setFilterById(downloadId));
                if (c == null) return;
                try {
                    if (!c.moveToFirst()) return;
                    int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        Uri uri = dm.getUriForDownloadedFile(downloadId);
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(uri, "application/vnd.android.package-archive");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        toast("Download fehlgeschlagen. Versuch es später nochmal.");
                    } else {
                        pollDownload(dm);
                    }
                } finally {
                    c.close();
                }
            }
        }, 1000);
    }

    private void toast(final String msg) {
        main.post(new Runnable() {
            @Override public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show(); }
        });
    }
}
