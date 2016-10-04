package org.medicmobile.webapp.mobile;

import android.app.DatePickerDialog;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.TrafficStats;
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
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

public class MedicAndroidJavascript {
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	private final EmbeddedBrowserActivity parent;

	private LocationManager locationManager;
	private Alert soundAlert;

	public MedicAndroidJavascript(EmbeddedBrowserActivity parent) {
		this.parent = parent;
	}

	public void setAlert(Alert soundAlert) {
		this.soundAlert = soundAlert;
	}

	public void setLocationManager(LocationManager locationManager) {
		this.locationManager = locationManager;
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

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void datePicker(final String targetElement) {
		datePicker(targetElement, Calendar.getInstance());
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void datePicker(final String targetElement, String initialDate) {
		try {
			Calendar c = Calendar.getInstance();
			c.setTime(DATE_FORMAT.parse(initialDate));
			datePicker(targetElement, c);
		} catch(ParseException ex) {
			datePicker(targetElement);
		}
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
				String dateString = String.format("%04d-%02d-%02d", year, month, day);
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

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | MedicAndroidJavascript::" +
				String.format(message, extras));
	}
}
