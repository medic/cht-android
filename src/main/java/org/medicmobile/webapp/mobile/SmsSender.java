package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import java.util.ArrayList;

import static java.lang.Integer.toHexString;
import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static android.app.Activity.RESULT_OK;
import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;
import static android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE;
import static android.telephony.SmsManager.RESULT_ERROR_NULL_PDU;
import static android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF;

class SmsSender {
	private static final int UNUSED_REQUEST_CODE = 0;
	private static final String DEFAULT_SMSC = null;

	private static final String SENDING_REPORT = "medic.android.sms.SENDING_REPORT";
	private static final String DELIVERY_REPORT = "medic.android.sms.DELIVERY_REPORT";

	private final EmbeddedBrowserActivity parent;
	private final SmsManager smsManager;

	SmsSender(EmbeddedBrowserActivity parent) {
		this.parent = parent;
		this.smsManager = SmsManager.getDefault();

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

	void send(String id, String destination, String content) {
		ArrayList<String> parts = smsManager.divideMessage(content);
		smsManager.sendMultipartTextMessage(destination,
				DEFAULT_SMSC,
				parts,
				intentsFor(SENDING_REPORT, id, destination, content, parts),
				intentsFor(DELIVERY_REPORT, id, destination, content, parts));
	}

//> PRIVATE HELPERS
	private void reportStatus(Intent intent, String status) {
		reportStatus(intent, status, null);
	}

	private void reportStatus(Intent intent, String status, String detail) {
		String id = intent.getStringExtra("id");
		String destination = intent.getStringExtra("destination");
		String content = intent.getStringExtra("content");
		int part = intent.getIntExtra("part", -1);

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

	private ArrayList<PendingIntent> intentsFor(String intentType, String id, String destination, String content, ArrayList<String> parts) {
		int totalParts = parts.size();
		ArrayList<PendingIntent> intents = new ArrayList<>(totalParts);

		for(int partIndex=0; partIndex<totalParts; ++partIndex) {
			intents.add(intentFor(intentType, id, destination, content, partIndex, totalParts));
		}

		return intents;
	}

	private PendingIntent intentFor(String intentType, String id, String destination, String content, int partIndex, int totalParts) {
		Intent intent = new Intent(intentType);
		intent.putExtra("id", id);
		intent.putExtra("destination", destination);
		intent.putExtra("content", content);
		intent.putExtra("partIndex", partIndex);
		intent.putExtra("totalParts", totalParts);

		// Use a random number for the PendingIntent's requestCode - we
		// will never want to cancel these intents, and we do not want
		// collisions.  There is a small chance of collisions if two
		// SMS are in-flight at the same time and are given the same id.

		return PendingIntent.getBroadcast(parent, UNUSED_REQUEST_CODE, intent, PendingIntent.FLAG_ONE_SHOT);
	}

//> STATIC HELPERS
	/**
	 * @see https://developer.android.com/reference/android/telephony/SmsMessage.html#createFromPdu%28byte[],%20java.lang.String%29
	 */
	@SuppressLint("ObsoleteSdkInt")
	private  static SmsMessage createFromPdu(Intent intent) {
		byte[] pdu = intent.getByteArrayExtra("pdu");
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			String format = intent.getStringExtra("format");
			return SmsMessage.createFromPdu(pdu, format);
		} else {
			return SmsMessage.createFromPdu(pdu);
		}
	}

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
		 * @see https://developer.android.com/reference/android/telephony/SmsMessage.html#getStatus%28%29
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
		 * @see http://www.etsi.org/deliver/etsi_ts/123000_123099/123040/13.01.00_60/ts_123040v130100p.pdf
		 */
		@SuppressWarnings("PMD.EmptyIfStmt")
		private void handleGsmDelivery(Intent intent, int status) {
			// Detail of the failure.  Must be set for FAILED messages.
			String fDetail = null;

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
}
