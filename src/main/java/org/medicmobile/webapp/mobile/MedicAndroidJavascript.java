package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.TrafficStats;
import android.os.Process;
import android.widget.DatePicker;

import java.io.PrintWriter;
import java.io.StringWriter;
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
import static org.medicmobile.webapp.mobile.MedicLog.log;

public class MedicAndroidJavascript {
	private static final String DATE_FORMAT = "yyyy-MM-dd";

	private final EmbeddedBrowserActivity parent;
	private final SimprintsSupport simprints;
	private final MrdtSupport mrdt;
	private final SmsSender smsSender;

	private LocationManager locationManager;
	private Alert soundAlert;

	public MedicAndroidJavascript(EmbeddedBrowserActivity parent) {
		this.parent = parent;
		this.simprints = parent.getSimprintsSupport();
		this.mrdt = parent.getMrdtSupport();
		this.smsSender = parent.getSmsSender();
	}

	public void setAlert(Alert soundAlert) {
		this.soundAlert = soundAlert;
	}

	public void setLocationManager(LocationManager locationManager) {
		this.locationManager = locationManager;
	}

//> JavascriptInterface METHODS
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
		try {
			if(soundAlert != null) soundAlert.trigger();
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public String getDataUsage() {
		try {
			int uid = Process.myUid();
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

	@Deprecated
	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	@SuppressLint("MissingPermission") // handled by catch(Exception)
	/**
	 * @deprecated Location should be fetched directly from the browser.
	 * @see https://github.com/medic/medic-webapp/issues/3781
	 */
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
		try {
			datePicker(targetElement, Calendar.getInstance());
		} catch(Exception ex) {
			logException(ex);
		}
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
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public boolean mrdt_available() {
		try {
			return mrdt.isAppInstalled();
		} catch(Exception ex) {
			logException(ex);
			return false;
		}
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void mrdt_verify() {
		try {
			mrdt.startVerify();
		} catch(Exception ex) {
			logException(ex);
		}
	}

	/**
	 * @return {@code true} iff an app is available to handle supported simprints {@code Intent}s
	 */
	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public boolean simprints_available() {
		try {
			return simprints.isAppInstalled();
		} catch(Exception ex) {
			logException(ex);
			return false;
		}
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void simprints_ident(int targetInputId) {
		try {
			simprints.startIdent(targetInputId);
		} catch(Exception ex) {
			logException(ex);
		}
	}

	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void simprints_reg(int targetInputId) {
		try {
			simprints.startReg(targetInputId);
		} catch(Exception ex) {
			logException(ex);
		}
	}


	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public boolean sms_available() {
		return smsSender != null;
	}

	/**
	 * @param id id associated with this message, e.g. a pouchdb docId
	 * @param destination the recipient phone number for this message
	 * @param content the text content of the SMS to be sent
	 */
	@org.xwalk.core.JavascriptInterface
	@android.webkit.JavascriptInterface
	public void sms_send(String id, String destination, String content) throws Exception {
		try {
			// TODO we may need to do this on a background thread to avoid the browser UI from blocking while the SMS is being sent.  Check.
			smsSender.send(id, destination, content);
		} catch(Exception ex) {
			logException(ex);
			throw ex;
		}
	}

//> PRIVATE HELPER METHODS
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

		// Make sure that the datepicker uses spinners instead of calendars.  Material design
		// does not support non-calendar view, so we explicitly use the Holo theme here.
		// Rumours suggest this may still show a calendar view on Android 24.  This has not been confirmed.
		// https://stackoverflow.com/questions/28740657/datepicker-dialog-without-calendar-visualization-in-lollipop-spinner-mode
		DatePickerDialog dialog = new DatePickerDialog(parent, android.R.style.Theme_Holo_Dialog, listener,
				initialDate.get(YEAR), initialDate.get(MONTH), initialDate.get(DAY_OF_MONTH));

		DatePicker picker = dialog.getDatePicker();
		picker.setCalendarViewShown(false);
		picker.setSpinnersShown(true);

		dialog.show();
	}

	private void logException(Exception ex) {
		log(ex, "Execption thrown in JavascriptInterface function.");

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		String stacktrace = sw.toString()
				.replace("\n", "; ")
				.replace("\t", " ");

		parent.errorToJsConsole("Execption thrown in JavascriptInterface function: %s", stacktrace);
	}

//> STATIC HELPERS
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
