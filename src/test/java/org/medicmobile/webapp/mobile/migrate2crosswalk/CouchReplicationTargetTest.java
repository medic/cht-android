package org.medicmobile.webapp.mobile.migrate2crosswalk;

import java.util.*;
import java.util.regex.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.*;

import org.robolectric.*;
import org.robolectric.annotation.*;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = org.medicmobile.webapp.mobile.BuildConfig.class)
public class CouchReplicationTargetTest {
	/** A very rough regex for matching JSON */
	private static final Pattern ANY_JSON = Pattern.compile("\\{.*\\}");
	private static final Pattern ANY_REV = Pattern.compile("\\d+-\\w+");

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
		JsonEntity response = target.get("/", queryParams());

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

//> unexpected internal paths
	@Test
	public void _unexpectedInternalPaths_GET_shouldThrowExceptions() throws Exception {
		// when
		try {
			target.get("/_something");
			fail("Expected exception.");
		} catch(UnsupportedInternalPathException ex) {
			// expected
		}
	}

	@Test
	public void _unexpectedInternalPaths_POST_shouldThrowExceptions() throws Exception {
		// when
		try {
			target.post("/_something", json());
			fail("Expected exception.");
		} catch(UnsupportedInternalPathException ex) {
			// expected
		}
	}

//> _changes
	@Test
	public void _changes_GET_shouldReturnEmptyList() throws Exception {
		// when
		JsonEntity response = target.get("/_changes", queryParams(
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
	public void _local_GET_shouldThrowDocNotFoundException() throws Exception {
		// when
		try {
			target.get("/_local/some-random-doc-id");
			fail("An exception should have been thrown.");
		} catch(DocNotFoundException ex) {
			// expected
		}
	}

	@Test
	public void _local_POST_shouldThrowUnimplementedEndpointException() throws Exception {
		// when
		try {
			target.post("/_local",
					emptyObject());
			fail("An exception should have been thrown.");
		} catch(UnimplementedEndpointException ex) {
			// expected
		}
	}

	@Test
	public void _local_PUT_shouldStoreLocalDocument() throws Exception {
		// when
		target.put("/_local/some-id", json(
				"val", "one"));

		// then
		assertDbContent("medic");
		assertDbContent("local",
				"_local/some-id", ANY_REV, ANY_JSON);
	}

//> _revs_diff
	@Test
	public void _revs_diff_POST_shouldReturnEmptyObject_ifSuppliedWithEmptyList() throws Exception {
		// when
		JsonEntity response = target.post(
				"/_revs_diff",
				emptyObject());

		// expect
		assertJson(response, emptyObject());
	}

	@Test
	public void _revs_diff_POST_shouldEchoCompleteList_ifNothingInDatabase() throws Exception {
		// when
		JsonEntity response = target.post(
				"/_revs_diff", json(
				"abc-123", array("1-aaa"),
				"def-456", array("2-bbb")));

		// expect
		assertJson(response, json(
			"abc-123", json(
				"missing", array("1-aaa")),
			"def-456", json(
				"missing", array("2-bbb"))
		));
	}

	@Test
	public void _revs_diff_POST_shouldNotIncludeItemsInDbWithMatchingIdAndRev() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-aaa",
						"val", "one"
					),
					json(
						"_id", "def-456",
						"_rev", "1-xxx",
						"val", "two"
					)
				),
				"new_edits", false));
		assertDbContent("medic",
				"abc-123", "1-aaa", "{ \"_id\":\"abc-123\", \"_rev\":\"1-aaa\", \"val\":\"one\" }",
				"def-456", "1-xxx", "{ \"_id\":\"def-456\", \"_rev\":\"1-xxx\", \"val\":\"two\" }");

		// when
		JsonEntity response = target.post(
				"/_revs_diff", json(
				"abc-123", array("1-aaa"),
				"def-456", array("2-bbb"),
				"ghi-789", array("3-ccc")));

		// expect
		assertJson(response, json(
			"def-456", json(
				"missing", array("2-bbb")),
			"ghi-789", json(
				"missing", array("3-ccc"))
		));
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
		JsonEntity response = target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one"
					)
				),
				"new_edits", false));

		// then
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

		// and
		assertJson(response, array(
			json(
				"ok", true,
				"id", "abc-123",
				"rev", "1-xxx"
			)
		));
	}

	@Test
	public void _bulk_docs_shouldSaveMultipleDocumentsd() throws Exception {
		// when
		JsonEntity response = target.post("/_bulk_docs", json(
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
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }",
				"def-456", "2-xxx", "{ \"_id\":\"def-456\", \"_rev\":\"2-xxx\", \"val\":\"two\" }");

		// and
		assertJson(response, array(
			json(
				"ok", true,
				"id", "abc-123",
				"rev", "1-xxx"
			),
			json(
				"ok", true,
				"id", "def-456",
				"rev", "2-xxx"
			)
		));
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
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

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
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

		// and
		// TODO test the response contents
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
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }",
				"def-456", "2-xxx", "{ \"_id\":\"def-456\", \"_rev\":\"2-xxx\", \"val\":\"two\" }");
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
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

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
		assertDbContent("medic",
				"abc-123", "2-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"2-xxx\", \"val\":\"two\" }");
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
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

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
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");
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
		assertDbContent("medic",
				"abc-123", "2-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"2-xxx\", \"val\":\"one\" }");

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
		assertDbContent("medic",
				"abc-123", "2-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"2-xxx\", \"val\":\"one\" }");
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

//> Requesting docs
	@Test
	public void nonExistentDocRequest_shouldReturn404() throws Exception {
		// given
		// no docs exist

		try {
			// when
			target.get("/abc-123", queryParams());
			fail("Expected exception.");
		} catch(DocNotFoundException ex) {
			// expected
		}
	}

	@Test
	public void existingDocRequest_shouldReturnDoc() throws Exception {
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
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

		// when
		JsonEntity response = target.get("/abc-123", queryParams());

		// expect
		assertJson(response, json(
				"_id", "abc-123",
				"_rev", "1-xxx",
				"val", "one"));
	}

//> HELPERS
	private void assertDbContent(String dbName, Object... args) throws JSONException {
		Object[] expectedContent = new Object[args.length];
		for(int i=0; i<args.length; i+=3) {
			expectedContent[i] = args[i];
			expectedContent[i+1] = args[i+1];

			Object jsonContent = args[i+2];
			if(jsonContent instanceof String) {
				// Convert to JSON and back to ensure consistent ordering
				expectedContent[i+2] = new JSONObject((String) jsonContent).toString();
			} else if(jsonContent instanceof Pattern) {
				expectedContent[i+2] = jsonContent;
			} else throw new RuntimeException("Don't know how to match object of class " + jsonContent.getClass());
		}
		db.assertTable(dbName, expectedContent);
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

	private static void assertJson(JsonEntity actual, JSONArray expected) {
		assertEquals(expected.toString(), actual.toString());
	}

	private static void assertJson(JsonEntity actual, JSONObject expected) {
		assertEquals(expected.toString(), actual.toString());
	}

	private static Map<String, List<String>> queryParams(String... params) {
		Map<String, List<String>> m = new HashMap<>();
		for(int i=0; i<params.length; i+=2)
			m.put(params[i], Arrays.asList(params[i+1]));
		return m;
	}
}
