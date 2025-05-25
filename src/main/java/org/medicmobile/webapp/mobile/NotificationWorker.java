package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NotificationWorker extends Worker {
    final String TAG = "NOTIFICATION_WORKER";
    final int EXECUTION_TIMEOUT = 10;
    final static String CHANNEL_ID = "cht_task_notifications";
    final static String CHANNEL_NAME = "CHT Task Notifications";

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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
            webView.addJavascriptInterface(new NotificationBridge(getApplicationContext(), latch), "CHTNotificationBridge");
            enableStorage(webView);

            webView.setWebViewClient(new WebViewClient() {
                public void onPageFinished(WebView view, String url) {
                    String js = "(async function (){" +
                            " const api = window.CHTCore.AndroidApi;" +
                            " if (api && api.v1 && api.v1.notificationTasks) {" +
                            "   const tasks = await api.v1.notificationTasks();" +
                            "   CHTNotificationBridge.onJsResult(JSON.stringify(tasks));" +
                            " }" +
                            "})();";
                    view.evaluateJavascript(js, null);
                }
            });
            webView.loadUrl(appUrl);
        });
        try {
            boolean completed = latch.await(EXECUTION_TIMEOUT, TimeUnit.SECONDS);
            if (completed) {
                Log.d(TAG, "notification worker ran successfully");
            } else {
                Log.d(TAG, "notification worker taking long to complete");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "error executing notification worker" + e);
            Thread.currentThread().interrupt();
            return Result.failure();
        }

        return Result.success();
    }

    private void enableStorage(WebView container) {
        WebSettings settings = container.getSettings();
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
    }

    public static class NotificationBridge {
        private final Context context;
        private final CountDownLatch latch;

        NotificationBridge(Context context, CountDownLatch latch) {
            this.context = context;
            this.latch = latch;
        }

        @JavascriptInterface
        public void onJsResult(String data) throws JSONException {
            JSONArray dataArray = parseData(data);
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject task = dataArray.getJSONObject(i);
                String contentText = task.getString("contentText");
                String title = task.getString("title");
                showNotification(i, title, contentText);
            }
            latch.countDown();
        }

        private JSONArray parseData(String data) {
            data = data.replace("^\"|\"$", "")
                    .replace("\\\"", "\"");
            try {
                return new JSONArray(data);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        private void showNotification(int id, String title, String contentText) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(contentText)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            manager.notify(id, builder.build());
        }
    }

}
