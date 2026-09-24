package org.medicmobile.webapp.mobile.offlinesync;

import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

/**
	* Holds bundles on disk between the wire and the webapp.
	*
	* A bundle is up to several megabytes of ciphertext, which is too much to pass across the
	* JavaScript bridge in one piece in either direction, so it rests here while the two sides move
	* it a chunk at a time. Nothing in this class can read a bundle: it is ciphertext addressed to
	* the server, and this device only ever carries it.
	*
	* Files live in app-private storage, so no other app on the device can reach them.
	*/
public class BundleSpool {

	/** Ids are generated here, so anything that does not look like one did not come from us. */
	private static final Pattern ID = Pattern.compile("[0-9a-f-]{36}");
	private static final String BUNDLE_SUFFIX = ".bundle";
	private static final String META_SUFFIX = ".json";

	private final File root;

	public BundleSpool(File root) {
		if (root == null) {
			throw new IllegalArgumentException("root must not be null");
		}
		this.root = root;
	}

	/** Bundles received from a peer, waiting for the webapp to take them. */
	public static BundleSpool inbox(Context context) {
		return new BundleSpool(new File(context.getApplicationContext().getFilesDir(), "offline-sync/inbox"));
	}

	/**
		* Bundles the webapp is handing over, waiting to be sent.
		*
		* In the cache directory because a bundle here is a copy of data the webapp still holds: if
		* the system reclaims it, the transfer fails and is packed again, and nothing is lost.
		*/
	public static BundleSpool outbox(Context context) {
		return new BundleSpool(new File(context.getApplicationContext().getCacheDir(), "offline-sync/outbox"));
	}

	/** Starts a new, empty bundle and returns its id. */
	public String create() throws IOException {
		if (!root.exists() && !root.mkdirs()) {
			throw new IOException("Could not create " + root);
		}
		String id = UUID.randomUUID().toString();
		if (!bundle(id).createNewFile()) {
			throw new IOException("Could not start a bundle file");
		}
		return id;
	}

	/** Appends to a bundle being assembled. */
	public void append(String id, byte[] bytes) throws IOException {
		try (FileOutputStream out = new FileOutputStream(bundle(id), true)) {
			out.write(bytes);
		}
	}

	/** Writes a bundle arriving from a peer, along with the envelope it came with. */
	public String store(String envelope, String signature, InputStream body) throws IOException {
		String id = create();
		try (FileOutputStream out = new FileOutputStream(bundle(id))) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = body.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
		writeMeta(id, envelope, signature);
		return id;
	}

	/**
		* Every bundle held here, as the webapp needs to see it: the envelope and signature to send
		* on, and the size so it knows how much there is to read.
		*/
	public JSONArray list() throws JSONException {
		JSONArray bundles = new JSONArray();
		File[] files = root.listFiles((unused, name) -> name.endsWith(BUNDLE_SUFFIX));
		if (files == null) {
			return bundles;
		}
		Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
		for (File file : files) {
			String id = file.getName().substring(0, file.getName().length() - BUNDLE_SUFFIX.length());
			JSONObject meta = readMeta(id);
			if (meta != null) {
				bundles.put(meta.put("id", id).put("bytes", file.length()));
			}
		}
		return bundles;
	}

	/**
		* Reads part of a bundle, clamped to what the file actually holds.
		*
		* The caller knows how much there is from the size reported by {@link #list()} and reads up
		* to it; a short result here means it asked beyond the end, not that it should stop.
		*/
	public byte[] read(String id, long offset, int length) throws IOException {
		try (RandomAccessFile file = new RandomAccessFile(bundle(id), "r")) {
			long available = Math.max(0, file.length() - offset);
			byte[] chunk = new byte[(int) Math.min(length, available)];
			if (chunk.length > 0) {
				file.seek(offset);
				file.readFully(chunk);
			}
			return chunk;
		}
	}

	public long size(String id) {
		return bundle(id).length();
	}

	/** Removes a bundle and its envelope. Safe to call for a bundle that is already gone. */
	public boolean delete(String id) {
		File meta = meta(id);
		boolean metaGone = !meta.exists() || meta.delete();
		return bundle(id).delete() && metaGone;
	}

	private void writeMeta(String id, String envelope, String signature) throws IOException {
		try {
			JSONObject meta = new JSONObject()
					.put("envelope", envelope)
					.put("signature", signature);
			try (FileOutputStream out = new FileOutputStream(meta(id))) {
				out.write(meta.toString().getBytes(StandardCharsets.UTF_8));
			}
		} catch (JSONException e) {
			throw new IOException("Could not record the bundle envelope", e);
		}
	}

	/** Null for a bundle whose envelope is missing or unreadable: it can never be sent on. */
	private JSONObject readMeta(String id) {
		try (InputStream in = new java.io.FileInputStream(meta(id))) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int read;
			while ((read = in.read(buffer)) != -1) {
				bytes.write(buffer, 0, read);
			}
			return new JSONObject(bytes.toString("UTF-8"));
		} catch (IOException | JSONException e) {
			warn(e, "Ignoring a bundle with no readable envelope");
			return null;
		}
	}

	/** Package-private so the endpoint can hand the file straight to the client. */
	File bundle(String id) {
		return new File(root, checked(id) + BUNDLE_SUFFIX);
	}

	private File meta(String id) {
		return new File(root, checked(id) + META_SUFFIX);
	}

	/**
		* Ids reach this class from the webapp, so they are checked before they become a path. The
		* shape is the one {@link #create()} produces; anything else is refused rather than escaped,
		* because there is no legitimate caller that would send something different.
		*/
	private String checked(String id) {
		if (id == null || !ID.matcher(id).matches()) {
			throw new IllegalArgumentException("not a bundle id");
		}
		return id;
	}
}
