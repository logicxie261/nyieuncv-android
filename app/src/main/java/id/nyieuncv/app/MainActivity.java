package id.nyieuncv.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * NyieunCV — pembungkus WebView untuk aplikasi CV offline.
 * Tanpa izin apa pun; konten dimuat dari aset lokal lewat WebViewAssetLoader;
 * semua permintaan jaringan diblokir.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String ORIGIN = "https://" + HOST;
    private static final String START_URL = ORIGIN + "/assets/index.html";

    private static final int REQ_PICK_FILE = 1001;
    private static final int REQ_SAVE_FILE = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingSaveText;
    private long lastBackPress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Android 15+ memaksa edge-to-edge; sisakan ruang untuk status bar, navigasi, dan keyboard.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime()
                                | WindowInsets.Type.displayCutout());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft();
                t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight();
                b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });

        configureWebView();

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);          // dibutuhkan aplikasi
        s.setDomStorageEnabled(true);          // localStorage menyimpan data CV
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse res = loader.shouldInterceptRequest(request.getUrl());
                if (res != null) return res;
                String scheme = request.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    // Blokir semua permintaan jaringan lain.
                    return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked",
                            null, new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                String scheme = u.getScheme();
                if (request.hasGesture() && ("http".equals(scheme) || "https".equals(scheme)
                        || "mailto".equals(scheme) || "tel".equals(scheme))) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (ActivityNotFoundException ignored) { }
                }
                return true; // jangan pernah navigasi keluar dari aplikasi
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    toast("Tidak ada aplikasi pemilih berkas");
                    return false;
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
    }

    /** Jembatan JS → native. Hanya dipercaya bila halaman aktif berasal dari aset lokal. */
    private class Bridge {
        @JavascriptInterface
        public void printPage(final String title) {
            runOnUiThread(() -> {
                if (!isTrustedPage()) return;
                String job = sanitize(title, "CV");
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                if (pm == null) return;
                PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(job);
                PrintAttributes attrs = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build();
                pm.print(job, adapter, attrs);
            });
        }

        @JavascriptInterface
        public void saveFile(final String name, final String mime, final String text) {
            runOnUiThread(() -> {
                if (!isTrustedPage() || text == null || text.length() > 20_000_000) return;
                String safeName = sanitize(name, "cv.json");
                if (!safeName.contains(".")) safeName += ".json";
                pendingSaveText = text;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json".equals(mime) ? "application/json" : "text/plain");
                i.putExtra(Intent.EXTRA_TITLE, safeName);
                try {
                    startActivityForResult(i, REQ_SAVE_FILE);
                } catch (ActivityNotFoundException e) {
                    pendingSaveText = null;
                    toast("Tidak bisa membuka dialog simpan berkas");
                }
            });
        }
    }

    private boolean isTrustedPage() {
        String url = webView.getUrl();
        return url != null && (url.equals(ORIGIN) || url.startsWith(ORIGIN + "/"));
    }

    private static String sanitize(String s, String fallback) {
        if (s == null) return fallback;
        String r = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (r.length() > 80) r = r.substring(0, 80);
        return r.isEmpty() ? fallback : r;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FILE) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
        } else if (requestCode == REQ_SAVE_FILE) {
            String text = pendingSaveText;
            pendingSaveText = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && text != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (out == null) throw new IOException("stream null");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    toast("Berkas disimpan");
                } catch (IOException | SecurityException e) {
                    toast("Gagal menyimpan berkas");
                }
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Beri kesempatan halaman menutup dialog/galeri dulu.
        webView.evaluateJavascript(
                "(function(){return !!(window.__androidBack && window.__androidBack());})()",
                value -> {
                    if ("true".equals(value)) return;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastBackPress < 2000) {
                        finish();
                    } else {
                        lastBackPress = now;
                        toast("Tekan sekali lagi untuk keluar");
                    }
                });
    }

    @Override
    protected void onPause() {
        // Pastikan data CV tertulis sebelum aplikasi masuk latar belakang.
        webView.evaluateJavascript("window.__flushSave && window.__flushSave()", null);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
