package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
public class BundleSpoolTest {

	private static final String ENVELOPE = "eyJ1c2VyIjoiY2h3In0=";
	private static final String SIGNATURE = "c2lnbmF0dXJl";

	@Rule public TemporaryFolder folder = new TemporaryFolder();

	private BundleSpool spool;

	@Before public void setUp() {
		spool = new BundleSpool(new File(folder.getRoot(), "inbox"));
	}

	private byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	@Test public void constructor_rejectsAMissingRoot() {
		assertThrows(IllegalArgumentException.class, () -> new BundleSpool(null));
	}

	@Test public void create_makesTheDirectoryItNeeds() throws Exception {
		String id = spool.create();

		assertEquals(0, spool.size(id));
	}

	@Test public void append_buildsABundleFromItsPieces() throws Exception {
		String id = spool.create();

		spool.append(id, bytes("one"));
		spool.append(id, bytes("two"));

		assertArrayEquals(bytes("onetwo"), spool.read(id, 0, 64));
	}

	@Test public void store_keepsTheBodyAndTheEnvelopeItCameWith() throws Exception {
		String id = spool.store(ENVELOPE, SIGNATURE, new ByteArrayInputStream(bytes("ciphertext")));

		JSONArray held = spool.list();
		assertEquals(1, held.length());
		assertEquals(id, held.getJSONObject(0).getString("id"));
		assertEquals(ENVELOPE, held.getJSONObject(0).getString("envelope"));
		assertEquals(SIGNATURE, held.getJSONObject(0).getString("signature"));
		assertEquals(10, held.getJSONObject(0).getInt("bytes"));
		assertArrayEquals(bytes("ciphertext"), spool.read(id, 0, 64));
	}

	/** The webapp reads a bundle in pieces up to the size it was told, so a read is clamped to it. */
	@Test public void read_returnsWhatIsThereAndNoMore() throws Exception {
		String id = spool.store(ENVELOPE, SIGNATURE, new ByteArrayInputStream(bytes("0123456789")));

		assertArrayEquals(bytes("0123"), spool.read(id, 0, 4));
		assertArrayEquals(bytes("456789"), spool.read(id, 4, 100));
		assertEquals(0, spool.read(id, 10, 4).length);
	}

	@Test public void list_ignoresABundleWhoseEnvelopeIsMissing() throws Exception {
		spool.create();

		assertEquals(0, spool.list().length());
	}

	/** Oldest first, because that is the order they should leave in. */
	@Test public void list_ordersByWhenTheyArrived() throws Exception {
		String older = spool.store(ENVELOPE, SIGNATURE, new ByteArrayInputStream(bytes("a")));
		String newer = spool.store(ENVELOPE, SIGNATURE, new ByteArrayInputStream(bytes("b")));
		// Set rather than slept for: file timestamps are only accurate to the second on some
		// filesystems, which would make a test that waits both slow and unreliable.
		assertTrue(spool.bundle(older).setLastModified(1_000_000_000_000L));
		assertTrue(spool.bundle(newer).setLastModified(2_000_000_000_000L));

		JSONArray held = spool.list();
		assertEquals(older, held.getJSONObject(0).getString("id"));
		assertEquals(newer, held.getJSONObject(1).getString("id"));
	}

	@Test public void delete_removesTheBundleAndItsEnvelope() throws Exception {
		String id = spool.store(ENVELOPE, SIGNATURE, new ByteArrayInputStream(bytes("ciphertext")));

		assertTrue(spool.delete(id));
		assertEquals(0, spool.list().length());
	}

	@Test public void delete_isFalseForSomethingThatIsNotThere() throws Exception {
		String id = spool.create();
		spool.delete(id);

		assertFalse(spool.delete(id));
	}

	/**
		* Ids arrive from the webapp and become a path, so anything that is not an id this class
		* produced is refused rather than escaped.
		*/
	@Test public void anIdThatIsNotOneOfOursNeverBecomesAPath() {
		for (String id : new String[] { "../../etc/passwd", "", null, "not-a-uuid", "/absolute" }) {
			assertThrows(IllegalArgumentException.class, () -> spool.size(id));
		}
	}
}
