package org.medicmobile.webapp.mobile.migrate2crosswalk;

import java.util.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.*;

import org.robolectric.*;
import org.robolectric.annotation.*;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = org.medicmobile.webapp.mobile.BuildConfig.class)
public class CouchReplicationTargetTest {
	private CouchReplicationTarget target;
	private DbTestHelper db;

	@Before
	public void setUp() throws Exception {
		this.target = new CouchReplicationTarget(RuntimeEnvironment.application);
		this.db = new DbTestHelper(RuntimeEnvironment.application);
	}

	@After
	public void tearDown() throws Exception {
		db.tearDown();
	}

//> / (root)
	@Test
	public void _root_GET_shouldReturnDbDetails() throws Exception {
		// when
		JSONObject response = target.get("/", queryParams());

		// expect
		assertJson(response, json(
				"db_name", "medic",
				"doc_count", 0,
				"doc_del_count", 0,
				"update_seq", 0,
				"purge_seq", 0,
				"compact_running", false,
				"disk_size", 0,
				"data_size", 0,
				"instance_start_time", 0, // TODO is this important?
				"disk_format_version", 0, // TODO what does this mean?
				"committed_update_seq", 0));
	}

//> _changes
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

//> _local
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

//> _revs_diff
	@Test
	public void _revs_diff_POST_shouldReturnEmptyObject() throws Exception {
		// when
		JSONObject response = target.post(
				"/_revs_diff",
				emptyObject());

		// expect
		assertJson(response, emptyObject());
	}

//> _bulk_docs
	@Test
	public void _bulk_docs_shouldIgnoreAnEmptyRequest() throws Exception {
		// when
		try {
			target.post("/_bulk_docs", json(
					"docs", emptyObject(),
					"new_edits", false));
			fail("Expected exception.");
		} catch(EmptyResponseException ex) {
			// expected
		}
	}

	@Test
	public void _bulk_docs_shouldSaveASingleDocument() throws Exception {
		// when
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					)
				),
				"new_edits", false));

		// then
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");
	}

	@Test
	public void _bulk_docs_shouldSaveMultipleDocumentsd() throws Exception {
		// when
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "def-456",
						"_rev", "2-xxx",
						"val", "two"
					)
				),
				"new_edits", false));

		// then
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }",
				"def-456", "{ \"_id\":\"def-456\", \"_rev\":\"2-xxx\", \"val\":\"two\" }");
	}

	@Test
	public void _bulk_docs_shouldIgnoreASingleDuplicateDocument() throws Exception {
		// when
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					)
				),
				"new_edits", false));

		// then
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

		// when
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					)
				),
				"new_edits", false));

		// then
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");
	}

	@Test
	public void _bulk_docs_shouldSaveMultipleDocumentsIgnoringDuplicates() throws Exception {
		// when
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "def-456",
						"_rev", "2-xxx",
						"val", "two"
					)
				),
				"new_edits", false));

		// then
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }",
				"def-456", "{ \"_id\":\"def-456\", \"_rev\":\"2-xxx\", \"val\":\"two\" }");
	}

	@Test
	public void _bulk_docs_shouldUpdateExistingDocs_ifRevIncreased() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					)
				),
				"new_edits", false));
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

		// when
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "2-xxx",
						"val", "two"
					)
				),
				"new_edits", false));

		// then
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"2-xxx\", \"val\":\"two\" }");
	}

	@Test
	public void _bulk_docs_shouldNotUpdateExistingDocs_ifRevSame() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					)
				),
				"new_edits", false));
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

		// when
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-yyy",
						"val", "bad"
					)
				),
				"new_edits", false));

		// then
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");
	}

	@Test
	public void _bulk_docs_shouldNotUpdateExistingDocs_ifRevLess() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "2-xxx",
						"val", "one"
					)
				),
				"new_edits", false));
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"2-xxx\", \"val\":\"one\" }");

		// when
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-yyy",
						"val", "bad"
					)
				),
				"new_edits", false));

		// then
		assertDbContent("abc-123", "{ \"_id\":\"abc-123\", \"_rev\":\"2-xxx\", \"val\":\"one\" }");
	}

	@Test
	public void _bulk_docs_shouldHandleMalformedRequests() throws Exception {
		// when
		try {
			target.post("/_bulk_docs", json("nothing", null));
			fail("Expected exception.");
		} catch(EmptyResponseException ex) {
			// expected
		}
	}

//> HELPERS
	private void assertDbContent(String... args) throws JSONException {
		Object[] expectedContent = new Object[args.length];
		for(int i=0; i<args.length; i+=2) {
			expectedContent[i] = args[i];
			// standardise ordering of object keys
			expectedContent[i+1] = new JSONObject(args[i+1]).toString();
		}
		db.assertTable("medic", expectedContent);
	}

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

	private static JSONArray array(Object... contents) {
		JSONArray a = new JSONArray();
		for(Object o : contents) a.put(o);
		return a;
	}

	private static JSONObject emptyObject() {
		return new JSONObject();
	}

	private static JSONArray emptyArray() {
		return new JSONArray();
	}

	private static void assertJson(JSONObject actual, JSONObject expected) {
		assertEquals(expected.toString(), actual.toString());
	}

	private static Map<String, List<String>> queryParams(String... params) {
		Map<String, List<String>> m = new HashMap<>();
		for(int i=0; i<params.length; i+=2)
			m.put(params[i], Arrays.asList(params[i+1]));
		return m;
	}
}
