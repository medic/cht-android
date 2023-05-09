package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.SEND_SMS;
import static android.app.Activity.RESULT_OK;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.app.PendingIntent.getBroadcast;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;
import static android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE;
import static android.telephony.SmsManager.RESULT_ERROR_NULL_PDU;
import static android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RequestCode;
import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static java.lang.Integer.toHexString;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class SmsSender {
	private static final int UNUSED_REQUEST_CODE = 0;
	private static final String DEFAULT_SMSC = null;

	private static final String SENDING_REPORT = "medic.android.sms.SENDING_REPORT";
	private static final String DELIVERY_REPORT = "medic.android.sms.DELIVERY_REPORT";

	private final EmbeddedBrowserActivity parent;
	private Sms sms;

	protected SmsSender(EmbeddedBrowserActivity parent) {
		this.parent = parent;

		parent.registerReceiver(new BroadcastReceiver() {
			@Override public void onReceive(Context ctx, Intent intent) {
				log(this, "onReceive() :: %s", intent.getAction());

				try {
					switch(intent.getAction()) {
						case SENDING_REPORT:
							new SendingReportHandler().handle(intent, getResultCode());
							break;
						case DELIVERY_REPORT:
							new DeliveryReportHandler().handle(intent);
							break;
						default:
							throw new IllegalStateException("Unexpected intent: " + intent);
					}
				} catch(Exception ex) {
					warn(ex, "BroadcastReceiver threw exception '%s' when processing intent: %s",
							ex.getClass(), ex.getMessage());
				}
			}
		}, createIntentFilter());
	}

	public static SmsSender createInstance(EmbeddedBrowserActivity parent) {
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return new LSmsSender(parent);
		} else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			return new RSmsSender(parent);
		}
		return new SmsSender(parent);
	}

	void send(Sms sms) {
		if (!checkPermissions()) {
			this.sms = sms;
			return;
		}

		sendSmsMultipart(sms);
	}

	void resumeProcess(int resultCode) {
		if (resultCode == RESULT_OK && this.sms != null) {
			sendSmsMultipart(this.sms);
			this.sms = null;
			return;
		}

		trace(this.parent, "SmsSender :: Cannot send sms without Send SMS permission. Sms ID=%s", this.sms.getId());
	}

	@TargetApi(31)
	protected SmsManager getManager() {
		return parent.getSystemService(android.telephony.SmsManager.class);
	}

	@TargetApi(23)
	protected int getBroadcastFlags() {
		return FLAG_ONE_SHOT | FLAG_IMMUTABLE;
	}

	/**
	 * @see <a href="https://developer.android.com/reference/android/telephony/SmsMessage#createFromPdu(byte[])">createFromPdu(byte[])</a>
	 */
	@TargetApi(23)
	protected SmsMessage createFromPdu(Intent intent) {
		byte[] pdu = intent.getByteArrayExtra("pdu");
		String format = intent.getStringExtra("format");
		return SmsMessage.createFromPdu(pdu, format);
	}

//> PRIVATE HELPERS

	private void sendSmsMultipart(Sms sms) {
		SmsManager smsManager = getManager();
		ArrayList<String> parts = smsManager.divideMessage(sms.getContent());

		smsManager.sendMultipartTextMessage(
			sms.getDestination(),
			DEFAULT_SMSC,
			parts,
			createIntentsFromSmsParts(SENDING_REPORT, sms, parts),
			createIntentsFromSmsParts(DELIVERY_REPORT, sms, parts)
		);
	}

	private boolean checkPermissions() {
		if (ContextCompat.checkSelfPermission(this.parent, SEND_SMS) == PERMISSION_GRANTED) {
			return true;
		}

		trace(this, "SmsSender :: Requesting permissions.");
		Intent intent = new Intent(this.parent, RequestSendSmsPermissionActivity.class);
		this.parent.startActivityForResult(intent, RequestCode.ACCESS_SEND_SMS_PERMISSION.getCode());
		return false;
	}

	private void reportStatus(Intent intent, String status) {
		reportStatus(intent, status, null);
	}

	private void reportStatus(Intent intent, String status, String detail) {
		String id = intent.getStringExtra("id");
		String destination = intent.getStringExtra("destination");
		String content = intent.getStringExtra("content");

		parent.evaluateJavascript(safeFormat(
				"angular.element(document.body).injector().get('AndroidApi').v1.smsStatusUpdate('%s', '%s', '%s', '%s', '%s')",
				id,
				destination,
				content,
				status,
				detail));
	}

	private String describe(Intent intent) {
		String id = intent.getStringExtra("id");
		String destination = intent.getStringExtra("destination");
		String content = intent.getStringExtra("content");
		int part = intent.getIntExtra("part", -1);
		return String.format("[id:%s to %s (part %s) content:%s]", id, destination, part, content);
	}

	private ArrayList<PendingIntent> createIntentsFromSmsParts(String intentType, Sms sms, ArrayList<String> parts) {
		int totalParts = parts.size();

		return IntStream
			.range(0, totalParts)
			.mapToObj(index -> intentFor(intentType, sms, index, totalParts))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private PendingIntent intentFor(String intentType, Sms sms, int partIndex, int totalParts) {
		Intent intent = new Intent(intentType);
		intent.putExtra("id", sms.getId());
		intent.putExtra("destination", sms.getDestination());
		intent.putExtra("content", sms.getContent());
		intent.putExtra("partIndex", partIndex);
		intent.putExtra("totalParts", totalParts);

		// Use a random number for the PendingIntent's requestCode - we
		// will never want to cancel these intents, and we do not want
		// collisions.  There is a small chance of collisions if two
		// SMS are in-flight at the same time and are given the same id.

		return getBroadcast(parent, UNUSED_REQUEST_CODE, intent, getBroadcastFlags());
	}

//> STATIC HELPERS
	private static IntentFilter createIntentFilter() {
		IntentFilter filter = new IntentFilter();

		filter.addAction(SENDING_REPORT);
		filter.addAction(DELIVERY_REPORT);

		return filter;
	}

//> INTENT HANDLERS
	class DeliveryReportHandler {
		/**
		 * Mask for differentiating GSM and CDMA message statuses.
		 * @see <a href="https://developer.android.com/reference/android/telephony/SmsMessage#getStatus()>getStatus()</a>
		 */
		private static final int GSM_STATUS_MASK = 0xFF;


	//> PUBLIC API
		public void handle(Intent intent) {
			log(this, "Received delivery report for message: %s", describe(intent));

			int status = createFromPdu(intent).getStatus();

			log(this, "Delivery status: 0x" + toHexString(status));

			if((status & GSM_STATUS_MASK) == status) {
				handleGsmDelivery(intent, status);
			} else {
				handleCdmaDelivery();
			}
		}

	//> INTERNAL METHODS
		/**
		 * Decode the status value as per ETSI TS 123 040 V13.1.0 (2016-04) 9.2.3.15 (TP-Status (TP-ST)).
		 * @see <a href="http://www.etsi.org/deliver/etsi_ts/123000_123099/123040/13.01.00_60/ts_123040v130100p.pdf">ETSI TS</a>
		 */
		@SuppressWarnings("PMD.EmptyIfStmt")
		private void handleGsmDelivery(Intent intent, int status) {
			// Detail of the failure.  Must be set for FAILED messages.
			String fDetail;

			if(status < 0x20) {
				//> Short message transaction completed
				switch(status) {
					case 0x00: //> Short message received by the SME
					case 0x01: //> Short message forwarded by the SC to the SME but the SC is unable to confirm delivery
						reportStatus(intent, "DELIVERED");
						return;
					case 0x02: // Short message replaced by the SC
						// Not sure what to do with this.
				}
				if(status < 0x10) {
					// These values are "reserved"
				} else {
					//> Values specific to each SC
				}
				// For now, we will just ignore statuses that we don't understand.
				return;
			} else if(status < 0x40) {
				//> Temporary error, SC still trying to transfer SM
				// no need to report this status yet
				return;
			} else if(status < 0x60) {
				//> Permanent error, SC is not making any more transfer attempts
				switch(status) {
					case 0x40: fDetail = "Remote procedure error"; break;
					case 0x41: fDetail = "Incompatible destination"; break;
					case 0x42: fDetail = "Connection rejected by SME"; break;
					case 0x43: fDetail = "Not obtainable"; break;
					case 0x44: fDetail = "Quality of service not available"; break;
					case 0x45: fDetail = "No interworking available"; break;
					case 0x46: fDetail = "SM Validity Period Expired"; break;
					case 0x47: fDetail = "SM Deleted by originating SME"; break;
					case 0x48: fDetail = "SM Deleted by SC Administration"; break;
					case 0x49: fDetail = "SM does not exist"; break;
					default:
						if(status < 0x50) fDetail = String.format("Permanent error (Reserved: 0x%s)", toHexString(status));
						else fDetail = "SMSC-specific permanent error: 0x" + toHexString(status);
				}
			} else if(status <= 0x7f) {
				//> Temporary error, SC is not making any more transfer attempts
				switch(status) {
					case 0x60: fDetail = "Congestion"; break;
					case 0x61: fDetail = "SME busy"; break;
					case 0x62: fDetail = "No response from SME"; break;
					case 0x63: fDetail = "Service rejected"; break;
					case 0x64: fDetail = "Quality of service not available"; break;
					case 0x65: fDetail = "Error in SME"; break;
					default:
						if(status < 0x70) fDetail = String.format("Temporary error (Reserved: 0x%s)", toHexString(status));
						else fDetail = "SMSC-specific temporary error: 0x" + toHexString(status);
				}
			} else throw new IllegalStateException("Unexpected status (> 0x7F) : 0x" + toHexString(status));

			reportStatus(intent, "FAILED", fDetail);
			log(this, "Delivering message to %s failed (cause: %s)", describe(intent), fDetail);
		}

		private void handleCdmaDelivery() {
			log(this, "Delivery reports not yet supported on CDMA devices.");
		}
	}

	class SendingReportHandler {
		void handle(Intent intent, int resultCode) {
			log(this, "Received sending report for message: %s", describe(intent));

			if(resultCode == RESULT_OK) {
				reportStatus(intent, "SENT");
			} else {
				String failureReason;
				switch(resultCode) {
					case RESULT_ERROR_GENERIC_FAILURE:
						failureReason = getGenericFailureReason(intent);
						break;
					case RESULT_ERROR_NO_SERVICE:
						failureReason = "no-service";
						break;
					case RESULT_ERROR_NULL_PDU:
						failureReason = "null-pdu";
						break;
					case RESULT_ERROR_RADIO_OFF:
						failureReason = "radio-off";
						break;
					default:
						failureReason = "unknown; resultCode=" + resultCode;
				}
				reportStatus(intent, "FAILED", failureReason);
				log(this, "Sending message %s failed. Cause: %s", describe(intent), failureReason);
			}
		}

		private String getGenericFailureReason(Intent intent) {
			if(intent.hasExtra("errorCode")) {
				int errorCode = intent.getIntExtra("errorCode", -1);
				return "generic; errorCode=" + errorCode;
			} else {
				return "generic; no errorCode supplied";
			}
		}
	}

	static class RSmsSender extends SmsSender {

		RSmsSender(EmbeddedBrowserActivity parent) {
			super(parent);
		}

		protected SmsManager getManager(){
			return SmsManager.getDefault();
		}
	}

	static class LSmsSender extends RSmsSender {
		LSmsSender(EmbeddedBrowserActivity parent) {
			super(parent);
		}

		protected int getBroadcastFlags() {
			return FLAG_ONE_SHOT;
		}

		protected SmsMessage createFromPdu(Intent intent) {
			return SmsMessage.createFromPdu(intent.getByteArrayExtra("pdu"));
		}
	}

	static class Sms {
		private final String id;
		private final String destination;
		private final String content;

		public Sms(String id, String destination, String content) {
			this.id = id;
			this.destination = destination;
			this.content = content;
		}

		public String getId() {
			return id;
		}

		public String getDestination() {
			return destination;
		}

		public String getContent() {
			return content;
		}
	}
}
