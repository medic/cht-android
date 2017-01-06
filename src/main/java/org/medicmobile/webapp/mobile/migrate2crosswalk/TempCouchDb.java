package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONException;
import org.json.JSONObject;

class TempCouchDb extends SQLiteOpenHelper {
	private static final int VERSION = 1;

	private static final String ALL = null, NO_GROUP = null;
	private static final String[] NO_ARGS = {};

	private static final String tblMEDIC = "medic";
	private static final String clmID = "_id";
	private static final String clmJSON = "json";

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

	void store(JSONObject o) throws JSONException {
		ContentValues v = new ContentValues();

		v.put(clmID, o.getString("_id"));
		v.put(clmJSON, o.toString());

		db.insert(tblMEDIC, null, v);
	}
}
