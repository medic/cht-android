package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static android.view.View.GONE;

/**
 * Displayed when there's not enough free space on device. See free_space_warning.xml.
 */
public class FreeSpaceWarningActivity extends Activity {
	/** Key for passing the name of the activity to go to after FreeSpaceWarningActivity. */
	static final String NEXT_ACTIVITY = "next-activity";

	/** Recommended minimum free space on the device, in bytes */
	static final long MINIMUM_SPACE = 200 * 1024 * 1024;

	public void onCreate(Bundle savedInstanceState) {
		if(DEBUG) log("Starting...");
		super.onCreate(savedInstanceState);

		setContentView(R.layout.free_space_warning);

		current: {
			long freeSpace = getFilesDir().getFreeSpace();
			TextView field = (TextView) findViewById(R.id.txtFreeSpaceCurrent);
			field.setText(String.format(
					getResources().getString(R.string.txtFreeSpaceCurrent),
					asMb(freeSpace)));
		}

		recommended: {
			TextView field = (TextView) findViewById(R.id.txtFreeSpaceRecommended);
			field.setText(String.format(
					getResources().getString(R.string.txtFreeSpaceRecommended),
					asMb(MINIMUM_SPACE)));
		}
	}

//> EVENT HANDLERS
	/**
	 * Defined in free_space_warning.xml
	 */
	public void evtContinue(View view) {
		Class next = (Class) getIntent().getSerializableExtra(NEXT_ACTIVITY);
		startActivity(new Intent(this, next));
		finish();
	}

	/**
	 * Defined in free_space_warning.xml
	 */
	public void evtQuit(View view) {
		finish();
	}

//> PRIVATE HELPERS
	private static long asMb(long bytes) {
		return bytes >> 20;
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | FreeSpaceWarningActivity :: " +
				String.format(message, extras));
	}
}
