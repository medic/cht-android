package org.medicmobile.webapp.mobile;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v4.app.ActivityCompat;

import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

/**
 * Shows a confirmation dialog that displays a "prominent" disclosure about how
 * the user geolocation data is used, asking to confirm whether to allow the app to
 * access the location or not.
 *
 * If the user accepts, a request to the API to access the location is made,
 * but Android will show another confirmation dialog. If the user decline the first
 * confirmation, the request to the API is omitted and the decision recorded to avoid
 * requesting the same next time.
 */
public class RequestPermissionDialog {

	private static final String[] LOCATION_PERMISSIONS = { Manifest.permission.ACCESS_FINE_LOCATION };

	/**
	 * Show the confirmation dialog unless the user has previously denied
	 * to share the location from this same dialog.
	 */
	public static void show(final EmbeddedBrowserActivity activity, final int requestCode) {
		final SettingsStore settings = SettingsStore.in(activity);
		if (settings.hasUserDeniedGeolocation()) {
			trace(activity, "RequestPermissionDialog.show() :: " +
					"user has previously denied to share location");
			activity.locationRequestResolved();
			return;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		// TODO Define the final message and localize
		AlertDialog alert = builder
			.setTitle("Use your location")
			.setIcon(android.R.drawable.ic_menu_mylocation)
			.setMessage("This app collects location data to enable [feature], [feature], ...")
			.setCancelable(true)
			.setPositiveButton("Turn on", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					trace(activity, "RequestPermissionDialog.show() :: " +
							"user accepted to share the location");
					ActivityCompat.requestPermissions(activity, LOCATION_PERMISSIONS, requestCode);
				}
			})
			.setNegativeButton("No thanks", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					trace(activity, "RequestPermissionDialog.show() :: " +
							"user denied to share the location");
					activity.locationRequestResolved();
					try {
						settings.setUserDeniedGeolocation();
					} catch (SettingsException e) {
						error(e, "Error recording negative to access location");
					}
				}
			})
			.create();
		alert.show();
	}
}
