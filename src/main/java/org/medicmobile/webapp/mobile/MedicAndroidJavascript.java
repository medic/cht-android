package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Process;
import android.widget.DatePicker;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.json.JSONException;
import org.json.JSONObject;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;
import static java.util.Locale.UK;

public class MedicAndroidJavascript {
	private static final String DATE_FORMAT = "yyyy-MM-dd";

	private final EmbeddedBrowserActivity parent;
	private final SimprintsSupport simprints;

	private LocationManager locationManager;
	private Alert soundAlert;

	// Define a listener that responds to location updates (dummy for now)
	private LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			System.out.println("GPS Update: " + location);
		}
		public void onStatusChanged(String provider, int status, Bundle extras) {}
		public void onProviderEnabled(String provider) {}
		public void onProviderDisabled(String provider) {}
	};

	public MedicAndroidJavascript(EmbeddedBrowserActivity parent) {
		this.parent = parent;
		this.simprints = new SimprintsSupport(parent);
	}

	public void setAlert(Alert soundAlert) {
		this.soundAlert = soundAlert;
	}

	public void setLocationManager(LocationManager locationManager) {
		this.locationManager = locationManager;
		// Force location update on init
		this.getLocation();
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public String getAppVersion() {
		try {
			return parent.getPackageManager()
					.getPackageInfo(parent.getPackageName(), 0)
					.versionName;
		} catch(Exception ex) {
			return jsonError("Error fetching app version: ", ex);
		}
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void playAlert() {
		if(soundAlert != null) soundAlert.trigger();
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public String getDataUsage() {
		int uid = Process.myUid();
		try {
			return new JSONObject()
					.put("system", getDataUsage(
							TrafficStats.getTotalRxBytes(),
							TrafficStats.getTotalTxBytes()))
					.put("app", getDataUsage(
							TrafficStats.getUidRxBytes(uid),
							TrafficStats.getUidTxBytes(uid)))
					.toString();
		} catch(Exception ex) {
			return jsonError("Problem fetching data usage stats.");
		}
	}

	private JSONObject getDataUsage(long rx, long tx) throws JSONException {
		return new JSONObject()
				.put("rx", rx)
				.put("tx", tx);
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	@SuppressLint("MissingPermission") // handled by catch(Exception)
	public String getLocation() {
		try {
			if(locationManager == null) return jsonError("LocationManager not set.  Cannot retrieve location.");

			String provider = locationManager.getBestProvider(new Criteria(), true);
			if(provider == null) return jsonError("No location provider available.");

			locationManager.requestSingleUpdate(provider, locationListener, null);

			Location loc = locationManager.getLastKnownLocation(provider);

			if(loc == null) return jsonError("Provider '" + provider + "' did not provide a location.");

			locationManager.requestLocationUpdates(provider, 5 * 60 * 1000, 0, locationListener);

			return new JSONObject()
					.put("lat", loc.getLatitude())
					.put("long", loc.getLongitude())
					.toString();
		} catch(Exception ex) {
			return jsonError("Problem fetching location: ", ex);
		}
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void datePicker(final String targetElement) {
		datePicker(targetElement, Calendar.getInstance());
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void datePicker(final String targetElement, String initialDate) {
		try {
			DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, UK);
			Calendar c = Calendar.getInstance();
			c.setTime(dateFormat.parse(initialDate));
			datePicker(targetElement, c);
		} catch(ParseException ex) {
			datePicker(targetElement);
		}
	}

	/**
	 * @return {@code true} iff an app is available to handle supported simprints {@code Intent}s
	 */
	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public boolean simprints_available() {
		return simprints.isAppInstalled();
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void simprints_ident(int targetInputId) {
		simprints.startIdent(targetInputId);
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void simprints_reg(int targetInputId) {
		simprints.startReg(targetInputId);
	}

	private void datePicker(String targetElement, Calendar initialDate) {
		// Remove single-quotes from the `targetElement` CSS selecter, as
		// we'll be using these to enclose the entire string in JS.  We
		// are not trying to properly escape these characters, just prevent
		// suprises from JS injection.
		final String safeTargetElement = targetElement.replace('\'', '_');

		DatePickerDialog.OnDateSetListener listener = new DatePickerDialog.OnDateSetListener() {
			public void onDateSet(DatePicker view, int year, int month, int day) {
				++month;
				String dateString = String.format(UK, "%04d-%02d-%02d", year, month, day);
				String setJs = String.format("$('%s').val('%s').trigger('change')",
						safeTargetElement, dateString);
				parent.evaluateJavascript(setJs);
			}
		};

		new DatePickerDialog(parent, listener, initialDate.get(YEAR), initialDate.get(MONTH), initialDate.get(DAY_OF_MONTH))
				.show();
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
