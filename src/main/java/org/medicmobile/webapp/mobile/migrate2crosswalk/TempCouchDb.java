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

class TempCouchDb extends SQLiteOpenHelper {
	private static final int VERSION = 1;

	private static final String DEFAULT_ORDERING = null, NO_GROUP = null;
	private static final String[] NO_ARGS = {};

	private static final String tblMEDIC = "medic";
	private static final String clmID = "_id";
	private static final String clmJSON = "json";

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

	public void onCreate(SQLiteDatabase db) {
		db.execSQL(String.format("CREATE TABLE %s (" +
					"%s TEXT PRIMARY KEY, " +
					"%s TEXT NOT NULL)",
				tblMEDIC, clmID, clmJSON));
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Hopefully we'll never be versioning this database, as it should
		// be removed before the next major version.
	}

	void store(JSONObject doc) throws IllegalDocException, JSONException {
		String docId = doc.getString("_id");
		if(docId.length() == 0) throw new IllegalDocException(doc);

		String newRev = doc.getString("_rev");
		if(!REV.matcher(newRev).matches()) throw new IllegalDocException(doc);

		Cursor c = null;
		try {
			c = db.query(tblMEDIC,
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
					db.update(tblMEDIC, forUpdate(doc), "_id=?", cols(docId));
				} // else ignore the old version
			} else {
				db.insert(tblMEDIC, null, forInsert(docId, doc));
			}
		} finally {
			if(c != null) try { c.close(); } catch(Exception ex) {}
		}
	}

//> STATIC HELPERS
	private static ContentValues forInsert(String docId, JSONObject doc) {
		ContentValues v = new ContentValues();

		v.put(clmID, docId);
		v.put(clmJSON, doc.toString());

		return v;
	}

	private static ContentValues forUpdate(JSONObject doc) {
		ContentValues v = new ContentValues();

		v.put(clmJSON, doc.toString());

		return v;
	}

	private static String[] cols(String... args) {
		return args;
	}

	private static int getRevNumber(String rev) {
		String[] parts = rev.split("-", 2);
		assert parts.length == 2: "Rev should be split into 2 parts exactly!";

		return Integer.parseInt(parts[0]);
	}
}

class IllegalDocException extends Exception {
	public IllegalDocException(JSONObject doc) {
		super("Badly formed doc: " + doc);
	}
}
