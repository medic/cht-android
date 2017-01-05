package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class TempCouchDb extends SQLiteOpenHelper {
	private static final int VERSION = 1;

	private final SQLiteDatabase db;

	private TempCouchDb(Context ctx) {
		super(ctx, "migrate2crosswalk", null, VERSION);
		db = getWritableDatabase();
	}

	public void onCreate(SQLiteDatabase db) {
		// TODO create tables
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Hopefully we'll never be versioning this database, as it should
		// be removed before the next major version.
	}
}
