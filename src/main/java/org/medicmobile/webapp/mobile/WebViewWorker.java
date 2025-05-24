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
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
                    String js = "( " +
                            "async function() { " +
                            "const tasks = await window.CHTCore.AndroidApi.v1.nota(); " +
                            "AndroidBridge.onJsResult(JSON.stringify(tasks)); " +
                            "} " +
                            ")();";
                    view.evaluateJavascript(js, null);
                }
            });
            webView.loadUrl(appUrl);
        });
        try {
            boolean success = latch.await(10, TimeUnit.SECONDS);
            if (success) {
                Log.d("NOTI", "success");
            } else {
                Log.d("NOTI", "taking so long");
            }
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
        public void onJsResult(String data) throws JSONException {
            JSONArray dataArray = parseData(data);
            //Toast.makeText(context, dataArray.getJSONObject(0).getString("_id"), Toast.LENGTH_LONG).show();
            //showNotification(data);
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject task = dataArray.getJSONObject(i);
                //String id = task.getString("_id");
                String contact = task.getString("contact");
                String title = task.getString("title");
                showNotification(i, title, contact);
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

        private void showNotification(int id, String title, String contact) {
            String channelId = "default_channel";
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(channelId, "JS Notifications", NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText("You have a " + title + " task for " + contact + " due today")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            manager.notify(id, builder.build());
        }
    }


    private void enableStorage(WebView container) {
        WebSettings settings = container.getSettings();
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
    }

}
