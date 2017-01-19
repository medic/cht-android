package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.medicmobile.webapp.mobile.MedicLog;

class TempCouchDb extends SQLiteOpenHelper {
	private static final int VERSION = 1;

	private static final String DEFAULT_ORDERING = null, NO_GROUP = null;
	private static final String[] NO_ARGS = {};
	private static final String SELECT_ALL = null;
	private static final String[] NO_SELECTION_ARGS = null;

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
		super(ctx, "migrate2crosswalk", null, VERSION);
		db = getWritableDatabase();
	}

//> EVENT HANDLERS
	@Override public void onCreate(SQLiteDatabase db) {
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

		CouchChangesFeed changes = new CouchChangesFeed();

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
