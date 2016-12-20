package org.medicmobile.webapp.mobile.webviewmigrate;

import java.util.*;

import org.json.*;
import org.junit.*;

import static org.junit.Assert.*;

public class CouchReplicationTargetTest {
	private CouchReplicationTarget target;

	@Before
	public void setUp() throws Exception {
		this.target = new CouchReplicationTarget();
	}

	@Test
	public void _changes_GET_shouldReturnEmptyList() throws Exception {
		// when
		JSONObject response = target.get("/_changes", queryParams(
				"a", "1",
				"b", "2"));

		// expect
		assertJson(response, json(
				"results", emptyArray(),
				"last_seq", 0)
				);
	}

	@Test
	public void _local_GET_shouldThrowUnimplementedEndpointException() throws Exception {
		// when
		try {
			target.get("/_local/some-random-doc-id");
			fail("An exception should have been thrown.");
		} catch(UnimplementedEndpointException ex) {
			// expected
		}
	}

	@Test
	public void _local_POST_shouldThrowUnimplementedEndpointException() throws Exception {
		// when
		try {
			target.post("/_local/some-random-doc-id",
					emptyObject());
			fail("An exception should have been thrown.");
		} catch(UnimplementedEndpointException ex) {
			// expected
		}
	}

	@Test
	public void _revs_diff_POST_shouldReturnEmptyObject() throws Exception {
		// when
		JSONObject response = target.post(
				"/_revs_diff",
				emptyObject());

		// expect
		assertJson(response, emptyObject());
	}

	@Test
	public void _bulk_docs_shouldIgnoreAnEmptyRequest() throws Exception {
		// when
		try {
			JSONObject response = target.post("/_bulk_docs", json(
					"docs", emptyObject(),
					"new_edits", false));
			fail("Expected exception.");
		} catch(EmptyResponseException ex) {
			// expected
		}
	}

	@Test
	public void _bulk_docs_shouldSaveASingleDocument() throws Exception {
		fail("Implement me.");
	}

	@Test
	public void _bulk_docs_shouldSaveMultipleDocumentsd() throws Exception {
		fail("Implement me.");
	}

	@Test
	public void _bulk_docs_shouldIgnoreASingleDuplicateDocument() throws Exception {
		fail("Implement me.");
	}

	@Test
	public void _bulk_docs_shouldSaveMultipleDocumentsIgnoringDuplicates() throws Exception {
		fail("Implement me.");
	}

	@Test
	public void _bulk_docs_shouldUpdateExistingDocs() throws Exception {
		fail("Implement me.");
	}

//> HELPERS
	private static JSONObject json(Object... args) throws JSONException {
		if(args.length == 1) {
			String json = (String) args[0];
			return (JSONObject) new JSONTokener(json).nextValue();
		}
		JSONObject o = new JSONObject();
		for(int i=0; i<args.length; i+=2) {
			String key = (String) args[i];
			Object val = args[i+1];
			o.put(key, val);
		}
		return o;
	}

	private static JSONObject emptyObject() {
		return new JSONObject();
	}

	private static JSONArray emptyArray() {
		return new JSONArray();
	}

	private static void assertJson(JSONObject expected, JSONObject actual) {
		assertEquals(expected.toString(), actual.toString());
	}

	private static Map<String, String> queryParams(String... params) {
		Map<String, String> m = new HashMap<>();
		for(int i=0; i<params.length; i+=2)
			m.put(params[i], params[i+1]);
		return m;
	}
}
