package org.medicmobile.webapp.mobile;

import android.content.*;
import android.location.*;
import android.webkit.*;

import org.json.*;

public class MedicAndroidJavascript {
	private LocationManager locationManager;
	private SoundAlert soundAlert;
	private String versionName;

	public void setSoundAlert(SoundAlert soundAlert) {
		this.soundAlert = soundAlert;
	}

	public void setLocationManager(LocationManager locationManager) {
		this.locationManager = locationManager;
	}

	public void setAppVersion(String version) {
		this.versionName = version;
	}

	@JavascriptInterface
	public String getAppVersion() {
		return versionName;
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
			return jsonError("Problem fetching location: " + ex.getClass() + ": " + ex.getMessage());
		}
	}

	private static String jsonError(String cause) {
		return "{ \"error\": true, \"cause\":\"" +
				jsonEscape(cause) +
				"\" }";
	}

	private static String jsonEscape(String s) {
		return s.replaceAll("\"", "'");
	}
}

