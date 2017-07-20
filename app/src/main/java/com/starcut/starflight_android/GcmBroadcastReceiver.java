package com.starcut.starflight_android;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.starcut.starflight_client_android.StarFlightBroadcastReceiver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

public class GcmBroadcastReceiver extends StarFlightBroadcastReceiver {

    //Id for Notifications RANDOM
    public static final int NOTIFICATION_ID_REMIND = 12423;
    public static final int NOTIFICATION_ID_NORMAL = 43900;

    //Tags for Starflight
    public static final String TAG_NORMAL = "normal";
    public static final String TAG_REMIND = "remind";

    //Type of Broadcast according to the tag
    public static final String BROADCAST_ACTION_REMIND = "com.starcut.starflight-android.REMIND";
    public static final String BROADCAST_ACTION_NORMAL = "com.starcut.starflight-android.NORMAL";

    private String mNotificationType = null;

    @Override
    public void onReceive(final Context context,
                          final String text,
                          final String url,
                          final UUID messageUuid,
                          final JSONObject options) {
        Log.d(GcmBroadcastReceiver.class.getSimpleName(), "GCM.onReceive: New notification");
        try {
            if (options.has("tags")) {
                JSONArray tags = new JSONArray(options.getString("tags"));
                if (tags.length() != 1) {
                    Log.e(GcmBroadcastReceiver.class.getSimpleName(), "GCM.onReceive: There is more than one tag in the push notification!");
                    return;
                }

                String tag = tags.getString(0);
                if (tag.equals(TAG_NORMAL)) {
                     mNotificationType = BROADCAST_ACTION_NORMAL;
                } else if (tag.equals(TAG_REMIND)) {
                    mNotificationType = BROADCAST_ACTION_REMIND;
                }
            } else {
                mNotificationType = BROADCAST_ACTION_NORMAL;
            }

            Log.d("NOTIF", "Type " + mNotificationType);
            if (mNotificationType != null) {
                handlePushNotification(context, text);
            }
        } catch (Exception ex) {
            Log.e(GcmBroadcastReceiver.class.getSimpleName(), "onReceive: Could not read push notification data!", ex);
        }
    }

    /**
     * Show notification in case the application is on background and the preference to show notification is set to true.
     * Otherwise, broadcast that a push notification arrived so that UI views can be updated.
     */
    private void handlePushNotification(final Context context, final String text) {
        Intent intent = new Intent(context, MainActivity.class);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, 0);
        showNotification(notificationManager, getNotificationBuilder(context, text, contentIntent));
    }

    private void showNotification(final NotificationManager notificationManager,
                                  final NotificationCompat.Builder mBuilder) {

        if (mNotificationType.equals(BROADCAST_ACTION_REMIND)) {
            notificationManager.notify(NOTIFICATION_ID_REMIND, mBuilder.build());
        } else if (mNotificationType.equals(BROADCAST_ACTION_NORMAL)){
            notificationManager.notify(NOTIFICATION_ID_NORMAL, mBuilder.build());
        }
    }

    private NotificationCompat.Builder getNotificationBuilder(final Context context,
                                                              final String text,
                                                              final PendingIntent contentIntent) {

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(getNotificationIcon())
                .setContentTitle(context.getString(R.string.app_name))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setVibrate(new long[]{1000, 500, 200, 500, 1000})
                .setLights(Color.GREEN, 3000, 3000)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
                notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        return notificationBuilder;
    }

    /**
     * From Android LOLLIPOP up, the icon should be a silhouette to give a nice look with the white pattern.
     * Otherwise, just show the app icon.
     */
    private int getNotificationIcon() {
        boolean useWhiteIcon = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP);
        return useWhiteIcon ? R.mipmap.ic_launcher : R.mipmap.ic_launcher;
    }

}
