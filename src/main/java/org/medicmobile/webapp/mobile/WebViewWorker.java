package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebViewWorker extends Worker {

    public WebViewWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @NonNull
    @Override
    public Result doWork() {
        SettingsStore settings = SettingsStore.in(getApplicationContext());
        String appUrl = settings.getAppUrl();
        CountDownLatch latch = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            WebView webView = new WebView(getApplicationContext());
            webView.getSettings().setJavaScriptEnabled(true);

            webView.addJavascriptInterface(new JSBridge(getApplicationContext(), latch), "AndroidBridge");
            enableStorage(webView);

            webView.setWebViewClient(new WebViewClient() {
                public void onPageFinished(WebView view, String url) {
                    //Toast.makeText(getApplicationContext(), "hello----" + appUrl, Toast.LENGTH_LONG).show();
                    view.evaluateJavascript("AndroidBridge.onJsResult(window.CHTCore.AndroidApi.v1.nota());", null);
                }
            });
            webView.loadUrl(appUrl);
        });
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return Result.failure();
        }

        return Result.success();
    }

    public static class JSBridge {
        private final Context context;
        private final CountDownLatch latch;

        JSBridge(Context context, CountDownLatch latch) {
            this.context = context;
            this.latch = latch;
        }

        @JavascriptInterface
        public void onJsResult(String data) {
            showNotification(data);
            latch.countDown();
        }

        private void showNotification(String data) {
            String channelId = "default_channel";
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(channelId, "JS Notifications", NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("JS Data")
                    .setContentText(data)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            manager.notify(101, builder.build());
        }
    }



    private void enableStorage(WebView container) {
        WebSettings settings = container.getSettings();
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
    }

}
