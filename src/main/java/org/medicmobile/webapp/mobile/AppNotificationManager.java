package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class AppNotificationManager {
  //final String TAG = "NOTIFICATION_WORKER";
  private static final String CHANNEL_ID = "cht_task_notifications";
  private static final String CHANNEL_NAME = "CHT Task Notifications";
  private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

  private final Context context;
  //private Activity activity;

  public AppNotificationManager(Context context) {
    this.context = context;
    createNotificationChannel();
  }

  public boolean hasNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return ContextCompat.checkSelfPermission(context,
              Manifest.permission.POST_NOTIFICATIONS)
              == PackageManager.PERMISSION_GRANTED;
    }
    //versions below 13
    return true;
  }

  public void requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (context instanceof Activity activity) {
        if (!hasNotificationPermission()) {
          ActivityCompat.requestPermissions(activity,
                  new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
        }
      }
    }
  }

  void showNotification(int id, String title, String contentText) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    manager.notify(id, builder.build());
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel notificationChannel = new NotificationChannel(
              CHANNEL_ID,
              CHANNEL_NAME,
              NotificationManager.IMPORTANCE_DEFAULT
      );
      NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      manager.createNotificationChannel(notificationChannel);
    }
  }

}
