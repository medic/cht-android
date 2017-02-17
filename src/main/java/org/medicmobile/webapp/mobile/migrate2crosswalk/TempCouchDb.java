package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.medicmobile.webapp.mobile.MedicLog;

import static android.database.DatabaseUtils.appendEscapedSQLString;

// TODO rename this class as e.g. TempCouchBackingDb
public class TempCouchDb extends SQLiteOpenHelper {
	private static final int VERSION = 1;

	private static final String DEFAULT_ORDERING = null, NO_GROUP = null;
	private static final String[] NO_ARGS = {};
	private static final String SELECT_ALL = null;
	private static final String[] NO_SELECTION_ARGS = null;

	private static final String tblDELETED = "deleted_docs";
	private static final String tblLOCAL = "local";
	private static final String tblMEDIC = "medic";
	private static final String clmID = "_id";
	private static final String clmSEQ = "seq";
	private static final String clmREV = "_rev";
	private static final String clmJSON = "json";

	private static final String[] DOC_TABLES = { tblLOCAL, tblMEDIC };

	private static final Pattern REV = Pattern.compile("\\d+-\\w+");

	private static TempCouchDb _instance;
	public static synchronized TempCouchDb getInstance(Context ctx) {
		if(_instance == null) {
			_instance = new TempCouchDb(ctx);
		}

		return _instance;
	}

	private final SQLiteDatabase db;

	private TempCouchDb(Context ctx) {
		super(ctx, "migrate2crosswalk", new CursorDebugFactory(), VERSION);
		db = getWritableDatabase();
	}

//> EVENT HANDLERS
	@Override public void onCreate(SQLiteDatabase db) {
		db.execSQL(String.format("CREATE TABLE %s (" +
					"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
					"%s TEXT KEY, " +
					"%s TEXT NOT NULL)",
				tblDELETED, clmID, clmREV));
		for(String tableName : DOC_TABLES) {
			db.execSQL(String.format("CREATE TABLE %s (" +
						"%s INTEGER PRIMARY KEY AUTOINCREMENT, " +
						"%s TEXT KEY, " +
						"%s TEXT NOT NULL, " +
						"%s TEXT NOT NULL)",
					tableName, clmSEQ, clmID, clmREV, clmJSON));
		}
	}

	@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Hopefully we'll never be versioning this database, as it should
		// be removed before the next major version.
	}

//> DATA ACCESSORS
	boolean exists(String docId, String rev) throws JSONException {
		Cursor c = null;
		try {
			c = db.query(tblMEDIC,
					cols(clmID),
					"_id=? AND _rev=?", vals(docId, rev),
					NO_GROUP, NO_GROUP,
					DEFAULT_ORDERING,
					Integer.toString(1));

			return c.getCount() > 0;
		} finally {
			if(c != null) try { c.close(); } catch(Exception ex) {}
		}
	}

	JSONObject get(String docId) throws JSONException {
		return get(tblMEDIC, docId);
	}

	JSONObject get_local(String docId) throws JSONException {
		return get(tblLOCAL, docId);
	}

	private JSONObject get(String tableName, String docId) throws JSONException {
		Cursor c = null;
		try {
			c = db.query(tableName,
					cols(clmJSON),
					"_id=?", cols(docId),
					NO_GROUP, NO_GROUP,
					DEFAULT_ORDERING,
					Integer.toString(1));

			boolean exists = c.getCount() == 1;
			if(exists) {
				c.moveToNext();
				String json = c.getString(0);
				return (JSONObject) new JSONTokener(json).nextValue();
			}
			return null;
		} finally {
			if(c != null) try { c.close(); } catch(Exception ex) {}
		}
	}

	void store(JSONObject doc) throws IllegalDocException, JSONException {
		store(tblMEDIC, doc);
	}

	void store_local(JSONObject doc) throws IllegalDocException, JSONException {
		store(tblLOCAL, doc);
	}

	private void store(String tableName, JSONObject doc) throws IllegalDocException, JSONException {
		trace("store", "doc=%s", doc);
		String docId = doc.getString("_id");
		if(docId.length() == 0) throw new IllegalDocException(doc);

		String newRev = doc.getString("_rev");
		if(!REV.matcher(newRev).matches()) throw new IllegalDocException(doc);

		Cursor c = null;
		try {
			c = db.query(tableName,
					cols(clmJSON),
					"_id=?", cols(docId),
					NO_GROUP, NO_GROUP,
					DEFAULT_ORDERING,
					Integer.toString(1));
			boolean exists = c.getCount() == 1;

			if(exists) {
				c.moveToNext();
				String json = c.getString(0);
				JSONObject oldDoc = (JSONObject) new JSONTokener(json).nextValue();
				String oldRev = oldDoc.getString("_rev");

				if(getRevNumber(newRev) > getRevNumber(oldRev)) {
					db.update(tableName, forUpdate(doc), "_id=?", cols(docId));
				} // else ignore the old version
			} else {
				db.insert(tableName, null, forInsert(docId, doc));
			}
		} finally {
			if(c != null) try { c.close(); } catch(Exception ex) {}
		}
	}

	void storeDeleted(JSONObject doc) throws IllegalDocException, JSONException {
		trace("storeDeleted", "doc=%s", doc);

		String id = doc.optString("_id");
		String rev = doc.optString("_rev");

		if(id == null || id.length() == 0 ||
				rev == null || rev.length() == 0 ||
				!doc.optBoolean("_deleted", false))
			throw new IllegalDocException(doc);

		Cursor c = null;
		try {
			ContentValues v = new ContentValues();
			v.put(clmID, id);
			v.put(clmREV, rev);

			db.insert(tblDELETED, null, v);
		} finally {
			if(c != null) try { c.close(); } catch(Exception ex) {}
		}
	}

	public List<JSONObject> getDeletedRevs(String id) throws JSONException {
		List<JSONObject> revs = new LinkedList();

		Cursor c = null;
		try {
			c = db.query(tblDELETED,
					cols(clmREV),
					"_id=?", vals(id),
					NO_GROUP, NO_GROUP,
					DEFAULT_ORDERING);

			while(c.moveToNext()) {
				String rev = c.getString(0);
				revs.add(JSON.obj(
					"_id", id,
					"_rev", rev,
					"_deleted", true
				));
			}

			return revs;
		} finally {
			if(c != null) try { c.close(); } catch(Exception ex) {}
		}
	}

	public CouchViewResult getAllDocs(String... ids) throws JSONException {
		int total;
		CouchViewResult docs;

		{
			Cursor c = null;
			try {
				c = db.query(tblMEDIC,
						cols(clmSEQ, clmJSON),
						SELECT_ALL, NO_SELECTION_ARGS,
						NO_GROUP, NO_GROUP,
						DEFAULT_ORDERING);

				docs = new CouchViewResult(c.getCount(), 0); // TODO we can do a proper count here if we care
			} finally {
				if(c != null) try { c.close(); } catch(Exception ex) {}
			}
		}

		{
			Cursor c = null;
			try {
				final String selecter;

				if(ids.length == 0) {
					selecter = SELECT_ALL;
				} else {
					StringBuilder bob = new StringBuilder();
					for(String id : ids) {
						bob.append(',');
						appendEscapedSQLString(bob, id);
					}

					selecter = clmID + " in (" + bob.substring(1) + ")";
				}

				c = db.query(tblMEDIC,
						cols(clmSEQ, clmJSON, clmID),
						selecter, NO_SELECTION_ARGS,
						NO_GROUP, NO_GROUP,
						DEFAULT_ORDERING);

				while(c.moveToNext()) {
					int seq = c.getInt(0);
					JSONObject json = (JSONObject) new JSONTokener(c.getString(1)).nextValue();
					docs.addDoc(seq, json);
				}

				return docs;
			} finally {
				if(c != null) try { c.close(); } catch(Exception ex) {}
			}
		}
	}

	public CouchChangesFeed getAllChanges() throws JSONException {
		CouchChangesFeed changes = new CouchChangesFeed();

		Cursor c = null;
		try {
			c = db.query(tblMEDIC,
					cols(clmSEQ, clmJSON),
					SELECT_ALL, NO_SELECTION_ARGS,
					NO_GROUP, NO_GROUP,
					DEFAULT_ORDERING);

			while(c.moveToNext()) {
				int seq = c.getInt(0);
				JSONObject json = (JSONObject) new JSONTokener(c.getString(1)).nextValue();
				changes.addDoc(seq, json);
			}

			return changes;
		} finally {
			if(c != null) try { c.close(); } catch(Exception ex) {}
		}
	}

	public CouchChangesFeed getChanges(Integer since, Integer limit) throws JSONException {
		if(since == null && limit == null) return getAllChanges();

		String selecter = since == null ? SELECT_ALL : gt(clmSEQ);
		String[] selectionArgs = since == null ? NO_SELECTION_ARGS : vals(since);

		CouchChangesFeed changes = new CouchChangesFeed(since);

		Cursor c = null;
		try {
			c = db.query(tblMEDIC,
					cols(clmSEQ, clmJSON),
					selecter, selectionArgs,
					NO_GROUP, NO_GROUP,
					DEFAULT_ORDERING,
					limit == null ? null : limit.toString());

			while(c.moveToNext()) {
				int seq = c.getInt(0);
				JSONObject json = (JSONObject) new JSONTokener(c.getString(1)).nextValue();
				changes.addDoc(seq, json);
			}

			return changes;
		} finally {
			if(c != null) try { c.close(); } catch(Exception ex) {}
		}
	}

//> DEBUG METHODS TODO remove these before merging with master
	public String executeRaw(String sql) {
		Cursor c = null;
		try {
			c = db.rawQuery(sql, NO_ARGS);
			int resultCount = c.getCount();

			StringBuilder bob = new StringBuilder();
			while(c.moveToNext()) {
				MedicLog.log("executeRaw() next row");
				int count = c.getColumnCount();
				for(int i=0; i<count; ++i) {
					if(i > 0) bob.append(" | ");
					String val = c.getString(i);
						MedicLog.log("executeRaw() %s: %s", i, val);
					bob.append(val);
				}
				bob.append('\n');
			}
			MedicLog.log("executeRaw() ----- cursor read fully.");
			if(resultCount > 1 && bob.length() > 640) return bob.substring(0, 639) + "...";
			return bob.toString();
		} finally {
			if(c != null) try { c.close(); } catch(Exception _) { /* ignore */ }
		}
	}

//> STATIC HELPERS
	private static ContentValues forInsert(String docId, JSONObject doc) throws JSONException {
		ContentValues v = new ContentValues();

		v.put(clmID, docId);
		v.put(clmREV, doc.getString("_rev"));
		v.put(clmJSON, doc.toString());

		return v;
	}

	private static ContentValues forUpdate(JSONObject doc) throws JSONException {
		ContentValues v = new ContentValues();

		v.put(clmREV, doc.getString("_rev"));
		v.put(clmJSON, doc.toString());

		return v;
	}

	private static String[] cols(String... args) {
		return args;
	}

	private static String[] vals(Object... args) {
		String[] vals = new String[args.length];
		for(int i=vals.length-1; i>=0; --i) vals[i] = args[i].toString();
		return vals;
	}

	private static String gt(String columnName) {
		return columnName + ">?";
	}

	private static int getRevNumber(String rev) {
		String[] parts = rev.split("-", 2);
		assert parts.length == 2: "Rev should be split into 2 parts exactly!";

		return Integer.parseInt(parts[0]);
	}

	private void trace(String methodName, String message, Object... args) {
		MedicLog.trace(this, methodName + "(): " + message, args);
	}
}

class IllegalDocException extends Exception {
	public IllegalDocException(JSONObject doc) {
		super("Badly formed doc: " + doc);
	}
}

class CouchChangesFeed {
	private int last_seq;
	private JSONArray results = new JSONArray();

	CouchChangesFeed() {}
	CouchChangesFeed(Integer since) {
		this.last_seq = since == null ? 0 : since;
	}

	public void addDoc(int seq, JSONObject doc) throws JSONException {
		last_seq = Math.max(seq, last_seq);
		results.put(JSON.obj(
			"changes", JSON.array(
				JSON.obj("rev", doc.getString("_rev"))
			),
			"id", doc.getString("_id"),
			"seq", seq
		));
	}

	public JSONObject get() throws JSONException {
		return JSON.obj("results", results,
				"last_seq", last_seq);
	}
}

class CouchViewResult {
	private final int totalRows;
	private final int offset;
	private JSONArray docs = new JSONArray();

	CouchViewResult(int totalRows, int offset) {
		this.totalRows = totalRows;
		this.offset = offset;
	}

	public void addDoc(int seq, JSONObject doc) throws JSONException {
		docs.put(JSON.obj(
			"id", doc.getString("_id"),
			"key", doc.getString("_id"),
			"value", JSON.obj("rev", doc.getString("_rev")),
			"doc", doc
		));
	}

	public JSONObject get() throws JSONException {
		return JSON.obj("offset", offset,
				"total_rows", totalRows,
				"rows", docs);
	}
}

class CursorDebugFactory implements CursorFactory {
int queryCount = 0;

	@Override
	public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, String editTable, SQLiteQuery query) {
		MedicLog.trace(this, query.toString());

		if(false && ++queryCount > 4) {
			throw new RuntimeException(String.format(
					"masterQuery=%s; editTable=%s; query=%s",
					masterQuery, editTable, query));
		}

		return new SQLiteCursor(masterQuery, editTable, query);
	}
}
