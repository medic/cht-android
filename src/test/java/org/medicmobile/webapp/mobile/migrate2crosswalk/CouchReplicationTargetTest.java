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
	public static final Pattern ANY_NATURAL_NUMBER = Pattern.compile("\\d+");

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
		FcResponse response = target.get("/", queryParams());

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

//> _all_docs
	@Test
	public void _all_docs_GET_shouldReturnAllDocs() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "aaa-111",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "bbb-222",
						"_rev", "1-yyy",
						"val", "two"
					)
				),
				"new_edits", false));

		// when
		FcResponse response = target.get("/_all_docs", noQueryParams());

		// then
		assertJson(response, json(
				"total_rows", 2,
				"offset", 0,
				"rows", array(
					json(
						"id", "aaa-111",
						"key", "aaa-111",
						"value", json("rev", "1-xxx"),
						"doc", json(
							"_id", "aaa-111",
							"_rev", "1-xxx",
							"val", "one"
						)
					),
					json(
						"id", "bbb-222",
						"key", "bbb-222",
						"value", json("rev", "1-yyy"),
						"doc", json(
							"_id", "bbb-222",
							"_rev", "1-yyy",
							"val", "two"
						)
					)
				)
			)
		);
	}

	@Test
	public void _all_docs_GET_shouldReturnDocRequestedInQueryParams() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "aaa-111",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "bbb-222",
						"_rev", "1-yyy",
						"val", "two"
					),
					json(
						"_id", "ccc-333",
						"_rev", "1-zzz",
						"val", "three"
					)
				),
				"new_edits", false));

		// when
		FcResponse response = target.get("/_all_docs", queryParams("key", "%22bbb-222%22"));

		// then
		assertJson(response, json(
				"total_rows", 3,
				"offset", 0,
				"rows", array(
					json(
						"id", "bbb-222",
						"key", "bbb-222",
						"value", json("rev", "1-yyy"),
						"doc", json(
							"_id", "bbb-222",
							"_rev", "1-yyy",
							"val", "two"
						)
					)
				)
			)
		);
	}

	@Test
	public void _all_docs_GET_shouldReturnAllDocsRequestedInQueryParams() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "aaa-111",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "bbb-222",
						"_rev", "1-yyy",
						"val", "two"
					),
					json(
						"_id", "ccc-333",
						"_rev", "1-zzz",
						"val", "three"
					)
				),
				"new_edits", false));

		// when
		FcResponse response = target.get("/_all_docs", queryParams("keys", "%5B%22aaa-111%22,%22ccc-333%22%5D"));

		// then
		assertJson(response, json(
				"total_rows", 3,
				"offset", 0,
				"rows", array(
					json(
						"id", "aaa-111",
						"key", "aaa-111",
						"value", json("rev", "1-xxx"),
						"doc", json(
							"_id", "aaa-111",
							"_rev", "1-xxx",
							"val", "one"
						)
					),
					json(
						"id", "ccc-333",
						"key", "ccc-333",
						"value", json("rev", "1-zzz"),
						"doc", json(
							"_id", "ccc-333",
							"_rev", "1-zzz",
							"val", "three"
						)
					)
				)
			)
		);
	}

	@Test
	public void _all_docs_POST_shouldReturnAllDocsRequestedInBody() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "aaa-111",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "bbb-222",
						"_rev", "1-yyy",
						"val", "two"
					),
					json(
						"_id", "ccc-333",
						"_rev", "1-zzz",
						"val", "three"
					)
				),
				"new_edits", false));

		// when
		FcResponse response = target.post("/_all_docs", json(
					"keys", array("aaa-111", "ccc-333")
				));

		// then
		assertJson(response, json(
				"total_rows", 3,
				"offset", 0,
				"rows", array(
					json(
						"id", "aaa-111",
						"key", "aaa-111",
						"value", json("rev", "1-xxx"),
						"doc", json(
							"_id", "aaa-111",
							"_rev", "1-xxx",
							"val", "one"
						)
					),
					json(
						"id", "ccc-333",
						"key", "ccc-333",
						"value", json("rev", "1-zzz"),
						"doc", json(
							"_id", "ccc-333",
							"_rev", "1-zzz",
							"val", "three"
						)
					)
				)
			)
		);
	}

//> _changes
	@Test
	public void _changes_GET_shouldReturnEmptyList_ifThereAreNoDocsInDb() throws Exception {
		// when
		FcResponse response = target.get("/_changes", queryParams(
				"a", "1",
				"b", "2"));

		// expect
		assertJson(response, json(
				"results", emptyArray(),
				"last_seq", 0)
				);
	}

	@Test
	public void _changes_GET_shouldReturnDocs() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "aaa-111",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "bbb-222",
						"_rev", "1-yyy",
						"val", "two"
					)
				),
				"new_edits", false));

		// when
		FcResponse response = target.get("/_changes", queryParams(
				"a", "1",
				"b", "2"));

		// then
		assertJson(response, json(
				"results", array(
					json(
						"changes", array(
							json("rev", ANY_REV)
						),
						"id", "aaa-111",
						"seq", 1
					),
					json(
						"changes", array(
							json("rev", ANY_REV)
						),
						"id", "bbb-222",
						"seq", 2
					)
				),
				"last_seq", 2)
		);
	}

	@Test
	public void _changes_GET_shouldRespectLimit() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "aaa-111",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "bbb-222",
						"_rev", "1-yyy",
						"val", "two"
					)
				),
				"new_edits", false));

		// when
		FcResponse response = target.get("/_changes", queryParams(
				"limit", "1"));

		// then
		assertJson(response, json(
				"results", array(
					json(
						"changes", array(
							json("rev", ANY_REV)
						),
						"id", "aaa-111",
						"seq", 1
					)
				),
				"last_seq", 1)
		);
	}

	@Test
	public void _changes_GET_shouldRespectSince() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "aaa-111",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "bbb-222",
						"_rev", "1-yyy",
						"val", "two"
					)
				),
				"new_edits", false));

		// when
		FcResponse response = target.get("/_changes", queryParams(
				"limit", "1",
				"since", "1"));

		// then
		assertJson(response, json(
				"results", array(
					json(
						"changes", array(
							json("rev", ANY_REV)
						),
						"id", "bbb-222",
						"seq", 2
					)
				),
				"last_seq", 2)
		);
	}

	@Test
	public void _changes_GET_shouldReturnCorrect_last_seq_whenNoResults() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "aaa-111",
						"_rev", "1-xxx",
						"val", "one"
					),
					json(
						"_id", "bbb-222",
						"_rev", "1-yyy",
						"val", "two"
					)
				),
				"new_edits", false));

		// when
		FcResponse response = target.get("/_changes", queryParams(
				"since", "2"));

		// then
		assertJson(response, json(
				"results", emptyArray(),
				"last_seq", 2)
		);
	}

	@Test
	public void _changes_GET_shouldRespectDeletedFlag() throws Exception {
		// TODO do we need this?
	}

//> _local
	@Test
	public void _local_GET_shouldThrowDocNotFoundException_ifDocDoesNotExist() throws Exception {
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

	@Test
	public void _local_GET_shouldReturnDoc_ifDocExists() throws Exception {
		// given
		target.put("/_local/some-id", json(
				"val", "one"));
		assertDbContent("local",
				"_local/some-id", ANY_REV, ANY_JSON);

		// when
		FcResponse response = target.get("/_local/some-id");

		// then
		assertJson(response, json("_id", "_local/some-id",
				"_rev", ANY_REV,
				"val", "one"));
	}

//> _revs_diff
	@Test
	public void _revs_diff_POST_shouldReturnEmptyObject_ifSuppliedWithEmptyList() throws Exception {
		// when
		FcResponse response = target.post(
				"/_revs_diff",
				emptyObject());

		// expect
		assertJson(response, emptyObject());
	}

	@Test
	public void _revs_diff_POST_shouldEchoCompleteList_ifNothingInDatabase() throws Exception {
		// when
		FcResponse response = target.post(
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
		FcResponse response = target.post(
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
		FcResponse response = target.post("/_bulk_docs", json(
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
		FcResponse response = target.post("/_bulk_docs", json(
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

//> _design
	@Test
	public void existingDdocRequest_shouldReturnDdoc() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "_design/my_ddoc",
						"_rev", "1-xxx",
						"val", "one"
					)
				),
				"new_edits", false));
		assertDbContent("medic",
				"_design/my_ddoc", "1-xxx", "{ \"_id\":\"_design/my_ddoc\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

		// when
		FcResponse response = target.get("/_design/my_ddoc", noQueryParams());

		// expect
		assertJson(response, json(
				"_id", "_design/my_ddoc",
				"_rev", "1-xxx",
				"val", "one"));
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
		FcResponse response = target.get("/abc-123", noQueryParams());

		// expect
		assertJson(response, json(
				"_id", "abc-123",
				"_rev", "1-xxx",
				"val", "one"));
	}

	@Test
	public void existingDocRequest_shouldReturnDoc_withColonInName() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc:123",
						"_rev", "1-xxx",
						"val", "one"
					)
				),
				"new_edits", false));
		assertDbContent("medic",
				"abc:123", "1-xxx", "{ \"_id\":\"abc:123\", \"_rev\":\"1-xxx\", \"val\":\"one\" }");

		// when
		FcResponse response = target.get("/abc:123", noQueryParams());

		// expect
		assertJson(response, json(
				"_id", "abc:123",
				"_rev", "1-xxx",
				"val", "one"));
	}

	@Test
	public void existingDocRequest_withOpenRevs_shouldReturnDocInArray() throws Exception {
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
		FcResponse response = target.get("/abc-123", queryParams("open_revs", "%5B%221-xxx%22%5D"));

		// expect
		assertJson(response, array(
				json("ok", json(
					"_id", "abc-123",
					"_rev", "1-xxx",
					"val", "one"))
				));
	}

	@Test
	public void attachmentRequest_shouldReturn404_ifDocDoesNotExist() throws Exception {
		// given
		// no docs exist

		try {
			// when
			target.get("/abc-123/attachment-1", queryParams());
			fail("Expected exception.");
		} catch(DocNotFoundException ex) {
			// expected
		}
	}

	@Test
	public void attachmentRequest_shouldReturn404_ifAttachmentDoesNotExist() throws Exception {
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

		try {
			// when
			target.get("/abc-123/attachment-2", queryParams());
			fail("Expected exception.");
		} catch(AttachmentNotFoundException ex) {
			// expected
		}
	}

	@Test
	public void attachmentRequest_shouldReturnExistingAttachment() throws Exception {
		// given
		target.post("/_bulk_docs", json(
				"docs", array(
					json(
						"_id", "abc-123",
						"_rev", "1-xxx",
						"val", "one",
						"_attachments", json(
							"attachment-1", json(
								"data", "aGk=", // 'hi', base64-encoded
								"digest", "NzY0ZWZhODgzZGRhMWUxMWRiNDc2NzFjNGEzYmJkOWU=",
								"content_type", "text/plain",
								"revpos", "1"
							)
						)
					)
				),
				"new_edits", false));
		assertDbContent("medic",
				"abc-123", "1-xxx", "{ \"_id\":\"abc-123\", \"_rev\":\"1-xxx\", \"val\":\"one\", \"_attachments\":{ \"attachment-1\":{" +
						"\"data\":\"aGk=\", \"digest\":\"NzY0ZWZhODgzZGRhMWUxMWRiNDc2NzFjNGEzYmJkOWU=\", \"content_type\":\"text/plain\", \"revpos\":\"1\" } } }");

		// when
		FcResponse response = target.get("/abc-123/attachment-1", queryParams());

		// then
		assertEquals("text/plain", response.contentType);

		// and
		assertEquals("hi", new String(response.bodyAsBytes(), "UTF-8"));
	}

//> HELPERS
	private void assertDbContent(String dbName, Object... args) throws JSONException {
		Object[] expectedContent = new Object[args.length + (args.length / 3)];

		for(int row=0; row<args.length / 3; ++row) {
			int x = row * 4;
			int a = row * 3;

			// Unlikely any test will care what the seq value is
			expectedContent[x] = ANY_NATURAL_NUMBER;

			expectedContent[x+1] = args[a];
			expectedContent[x+2] = args[a+1];

			Object jsonContent = args[a+2];
			if(jsonContent instanceof String) {
				// Convert to JSON and back to ensure consistent ordering
				expectedContent[x+3] = new JSONObject((String) jsonContent).toString();
			} else if(jsonContent instanceof Pattern) {
				expectedContent[x+3] = jsonContent;
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

	private static void assertJson(FcResponse actual, JSONArray expected) throws JSONException {
		assertJson(actual.asArray(), expected);
	}

	private static void assertJson(JSONArray actual, JSONArray expected) throws JSONException {
		if(actual == null && expected == null) return;

		String failMessage = String.format("Expected arrays are not equal: %s vs. %s", expected.toString(), actual.toString());

		assertEquals(failMessage, expected.length(), actual.length());
		for(int i=0; i<actual.length(); ++i) {
			aEq(expected.get(i), actual.get(i), failMessage);
		}
	}

	private static void assertJson(FcResponse actual, JSONObject expected) throws JSONException {
		assertJson(actual.asObject(), expected);
	}

	private static void assertJson(JSONObject actual, JSONObject expected) throws JSONException {
		String failMessage = String.format("Expected JSON objects are not equal: %s vs %s", expected.toString(), actual.toString());

		assertJson(actual.names(), expected.names());

		Iterator<String> keys = expected.keys();
		while(keys.hasNext()) {
			String key = keys.next();
			aEq(expected.get(key), actual.get(key), failMessage);
		}
	}

	private static void aEq(Object e, Object a, String failMessage) throws JSONException {
		if(e instanceof Pattern && a instanceof String) {
			assertTrue("Expected " + a + " to match regex " + e, ((Pattern) e).matcher((String) a).matches());
			return;
		}

		if(!a.getClass().equals(e.getClass()))
			fail(String.format("Objects are of different class: %s vs %s (%s)", e.getClass(), a.getClass(), failMessage));

		if(e instanceof JSONObject) {
			assertJson((JSONObject) a, (JSONObject) e);
		} else if(e instanceof JSONArray) {
			assertJson((JSONArray) a, (JSONArray) e);
		} else if(e instanceof String) {
			assertEquals(failMessage, (String) e, (String) a);
		} else if(e instanceof Integer) {
			assertEquals(failMessage, (int) e, (int) a);
		} else if(e instanceof Boolean) {
			assertEquals(failMessage, (boolean) e, (boolean) a);
		} else fail(String.format("Don't know how to compare objects of type %s & %s.", e.getClass(), a.getClass()));
	}

	private static Map<String, List<String>> noQueryParams() {
		return queryParams();
	}

	private static Map<String, List<String>> queryParams(String... params) {
		Map<String, List<String>> m = new HashMap<>();
		for(int i=0; i<params.length; i+=2)
			m.put(params[i], Arrays.asList(params[i+1]));
		return m;
	}
}
