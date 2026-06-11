package com.classmgr.hakgwan;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int FILECHOOSER_RESULT = 1001;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage (persist all settings on-device)
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new Bridge(), "AndroidFile");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "파일 선택"), FILECHOOSER_RESULT);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILECHOOSER_RESULT) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{ data.getData() };
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Auto-save today's file when the app goes to background (preserves original beforeunload behavior)
        if (webView != null) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new Event('beforeunload'));", null);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ─────────────────────────────────────────────
    //  JS ↔ Android bridge
    // ─────────────────────────────────────────────
    private class Bridge {

        @JavascriptInterface
        public void saveBase64(final String filename, final String b64, final String mime) {
            final byte[] data;
            try {
                data = Base64.decode(b64, Base64.DEFAULT);
            } catch (Exception e) {
                toast("저장 실패: 데이터 오류");
                return;
            }
            final String name = sanitize(filename);
            new Thread(new Runnable() {
                public void run() {
                    try {
                        saveToDownloads(name, data, mime);
                        toast("저장됨: 다운로드/학급관리/" + name);
                    } catch (Exception e) {
                        toast("저장 실패: " + e.getMessage());
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        String jobName = "학급관리 출석현황표";
                        PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(jobName);
                        pm.print(jobName, adapter, new PrintAttributes.Builder().build());
                    } catch (Exception e) {
                        toast("인쇄 실패: " + e.getMessage());
                    }
                }
            });
        }
    }

    private void saveToDownloads(String name, byte[] data, String mime) throws Exception {
        ContentResolver resolver = getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String relPath = Environment.DIRECTORY_DOWNLOADS + "/학급관리";

        // Overwrite if a file with the same name exists (matches original "same-date file is overwritten" behavior)
        Uri target = null;
        Cursor c = resolver.query(
                collection,
                new String[]{ MediaStore.Downloads._ID },
                MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + " LIKE ?",
                new String[]{ name, "%학급관리%" },
                null);
        if (c != null) {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                target = ContentUris.withAppendedId(collection, id);
            }
            c.close();
        }

        if (target != null) {
            OutputStream os = resolver.openOutputStream(target, "wt");
            try { os.write(data); } finally { os.close(); }
        } else {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            if (mime != null && mime.length() > 0) v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, relPath);
            target = resolver.insert(collection, v);
            if (target == null) throw new Exception("저장소 접근 불가");
            OutputStream os = resolver.openOutputStream(target);
            try { os.write(data); } finally { os.close(); }
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.length() == 0) return "untitled";
        String n = name.replace('/', '-').replace('\\', '-').replace(':', '-');
        n = n.replaceAll("[*?\"<>|]", "");
        return n.trim();
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show(); }
        });
    }
}
