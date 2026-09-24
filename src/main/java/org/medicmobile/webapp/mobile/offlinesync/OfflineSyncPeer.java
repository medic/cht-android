package org.medicmobile.webapp.mobile.offlinesync;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.content.Context;
import android.net.Network;
import android.util.Base64;

import java.io.File;
import java.io.IOException;

/**
	* The joining side of a pairing session.
	*
	* Takes the payload from a scanned QR code, joins the network it names, and confirms the device
	* answering there is the one the code came from. Pairing is only complete once that check passes:
	* reaching something at the address proves nothing on a network anyone can join.
	*/
public class OfflineSyncPeer {

	private static OfflineSyncPeer instance;

	private final HotspotJoiner joiner;
	/** Who hears about the pairing. Replaced when the screen that started it is recreated. */
	private volatile PairCallback callback;
	private final BundleSpool outbox;

	/**
		* Set once a host has proved who it is, and cleared when the session ends.
		*
		* Volatile because they are written on the connectivity callback thread and read on the
		* WebView thread, which is also why they are read once into locals below rather than twice.
		*/
	private volatile PeerClient host;
	private volatile QrCodeHelper.QrPayload hostAddress;

	public OfflineSyncPeer(HotspotJoiner joiner, BundleSpool outbox) {
		if (joiner == null || outbox == null) {
			throw new IllegalArgumentException("collaborators must not be null");
		}
		this.joiner = joiner;
		this.outbox = outbox;
	}

	private static OfflineSyncPeer create(Context context) {
		return new OfflineSyncPeer(HotspotJoiner.create(context), BundleSpool.outbox(context));
	}

	/**
		* Starts assembling a bundle, returning the id its pieces belong to.
		*
		* The webapp holds the bundle as bytes it cannot pass over in one call, so it opens one
		* here and writes it a piece at a time.
		*
		* @return the id, or null if this device has nowhere to put it
		*/
	public String openBundle() {
		try {
			return outbox.create();
		} catch (IOException e) {
			warn(e, "Could not open a bundle for sending");
			return null;
		}
	}

	/** Drops a bundle that will never be sent, so a failed attempt leaves nothing behind. */
	public boolean discardBundle(String id) {
		try {
			return outbox.delete(id);
		} catch (IllegalArgumentException e) {
			warn(e, "Refused to discard something that is not a bundle id");
			return false;
		}
	}

	/** Appends one base64 piece to a bundle being assembled. */
	public boolean writeBundle(String id, String base64Chunk) {
		try {
			outbox.append(id, Base64.decode(base64Chunk, Base64.NO_WRAP));
			return true;
		} catch (IOException | IllegalArgumentException e) {
			warn(e, "Could not write a piece of a bundle");
			return false;
		}
	}

	/**
		* The peer for this process. The joined network belongs to the process, and the screen is
		* recreated on a theme or language change, so a peer per screen could no longer leave it.
		*/
	public static synchronized OfflineSyncPeer getInstance(Context context) {
		if (instance == null) {
			instance = create(context.getApplicationContext());
		}
		return instance;
	}

	/**
		* Joins the session described by a scanned payload.
		*
		* @param payloadJson the QR contents as scanned, validated here before anything is joined
		* @param callback    reports the host's label once its key has been verified
		*/
	public void pair(String payloadJson, PairCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}
		this.callback = callback;

		QrValidation validation = QrCodeHelper.validateQrPayload(payloadJson);
		if (!validation.isAccepted()) {
			warn(OfflineSyncPeer.class, "Rejected a scanned code: " + validation.getDetail());
			callback.onFailed(validation.getCode());
			return;
		}

		QrCodeHelper.QrPayload payload = QrCodeHelper.parsePayload(payloadJson);
		if (payload == null) {
			callback.onFailed("unreadable_payload");
			return;
		}

		joiner.join(payload.getSsid(), payload.getPassword(), new HotspotJoiner.JoinCallback() {
			@Override public void onJoined(Network network) {
				verifyHost(network, payload, OfflineSyncPeer.this.callback);
			}

			@Override public void onFailed(String reason) {
				OfflineSyncPeer.this.callback.onFailed(reason);
			}

			@Override public void onLost() {
				// Also reached after pairing succeeded: this callback stays registered for the
				// session, so it is what notices a host that has walked away mid-transfer.
				forget();
				OfflineSyncPeer.this.callback.onFailed("network_lost");
			}
		});
	}

	/**
		* Confirms the host is the device the QR code came from, then reports success.
		*
		* A failure here is not a networking hiccup to retry quietly: it means something answered
		* that could not prove it was the host, so the session is abandoned.
		*/
	private void verifyHost(Network network, QrCodeHelper.QrPayload payload, PairCallback callback) {
		try {
			PeerClient client = new PeerClient(network, payload.getCertFingerprint());
			String label = client.fetchStatus(payload.getIpAddress(), payload.getPort());
			// Kept for the rest of the session: the same pinned client is what later carries the
			// bundles, so data only ever goes to the device that proved itself here.
			this.host = client;
			this.hostAddress = payload;
			log(OfflineSyncPeer.class, "Paired with " + label);
			callback.onPaired(label);
		} catch (IOException e) {
			warn(e, "Could not verify the host, abandoning the session");
			unpair();
			callback.onFailed("host_not_verified");
		}
	}

	/** Sends what happens to the pairing from now on to a new callback, e.g. a recreated screen. */
	public void redirect(PairCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}
		this.callback = callback;
	}

	/**
		* Hands one sealed bundle to the paired host, off the calling thread.
		*
		* The bundle file is deleted once the host has taken it. A failure leaves it in place: the
		* webapp packs the same data again next time rather than us retrying behind the user's back
		* on a link that may already be gone.
		*/
	public void sendBundle(String id, String envelope, String signature, SendCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}
		PeerClient client = host;
		QrCodeHelper.QrPayload address = hostAddress;
		if (client == null || address == null) {
			callback.onFailed("not_paired");
			return;
		}

		File body = outbox.bundle(id);
		new Thread(() -> {
			try {
				client.postBundle(address.getIpAddress(), address.getPort(), envelope, signature, body);
				discard(body);
				callback.onSent();
			} catch (PeerClient.BundleRejectedException e) {
				warn(e, "The host would not take the bundle");
				discard(body);
				callback.onFailed("bundle_rejected");
			} catch (IOException e) {
				warn(e, "Could not hand the bundle to the host");
				discard(body);
				callback.onFailed("transfer_failed");
			}
		}, "offline-sync-send").start();
	}

	/**
		* Removes a bundle once it is finished with, whether it arrived or not.
		*
		* Kept on failure it would only pile up: the webapp packs from its own position and would
		* build the same data again next time, so nothing here is worth resuming.
		*/
	private void discard(File body) {
		if (body.exists() && !body.delete()) {
			warn(OfflineSyncPeer.class, "Could not delete a bundle that is finished with");
		}
	}

	/** Leaves the session's network. */
	public void unpair() {
		forget();
		joiner.leave();
	}

	/** Drops the paired host, so anything sent after this fails as unpaired rather than hanging. */
	private void forget() {
		host = null;
		hostAddress = null;
	}

	public interface SendCallback {
		void onSent();

		/** @param reason a stable code the webapp can map to a message */
		void onFailed(String reason);
	}

	public interface PairCallback {
		/** @param hostLabel what the host calls itself, so the user can confirm the right device */
		void onPaired(String hostLabel);

		void onFailed(String reason);
	}
}
