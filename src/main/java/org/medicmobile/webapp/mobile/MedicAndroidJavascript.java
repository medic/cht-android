package org.medicmobile.webapp.mobile;

import android.content.*;
import android.content.pm.*;
import android.location.*;
import android.webkit.*;

import org.json.*;

public class MedicAndroidJavascript {
	private final Context ctx;
	private LocationManager locationManager;
	private Alert soundAlert;

	public MedicAndroidJavascript(Context ctx) {
		this.ctx = ctx;
	}

	public void setAlert(Alert soundAlert) {
		this.soundAlert = soundAlert;
	}

	public void setLocationManager(LocationManager locationManager) {
		this.locationManager = locationManager;
	}

	@JavascriptInterface
	public String getAppVersion() {
		try {
			return ctx.getPackageManager()
					.getPackageInfo(ctx.getPackageName(), 0)
					.versionName;
		} catch(Exception ex) {
			return jsonError("Error fetching app version: ", ex);
		}
	}

	@JavascriptInterface
	public void playAlert() {
		if(soundAlert != null) soundAlert.trigger();
	}

	@JavascriptInterface
	public String getLocation() {
		try {
			if(locationManager == null) return jsonError("LocationManager not set.  Cannot retrieve location.");

			String provider = locationManager.getBestProvider(new Criteria(), true);
			if(provider == null) return jsonError("No location provider available.");

			Location loc = locationManager.getLastKnownLocation(provider);

			if(loc == null) return jsonError("Provider '" + provider + "' did not provide a location.");

			return new JSONObject()
					.put("lat", loc.getLatitude())
					.put("long", loc.getLongitude())
					.toString();
		} catch(Exception ex) {
			return jsonError("Problem fetching location: ", ex);
		}
	}

	private static String jsonError(String message, Exception ex) {
		return jsonError(message + ex.getClass() + ": " + ex.getMessage());
	}

	private static String jsonError(String message) {
		return "{ \"error\": true, \"message\":\"" +
				jsonEscape(message) +
				"\" }";
	}

	private static String jsonEscape(String s) {
		return s.replaceAll("\"", "'");
	}
}

