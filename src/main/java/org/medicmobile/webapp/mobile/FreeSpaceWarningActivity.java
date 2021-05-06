package org.medicmobile.webapp.mobile;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.Utils.startAppActivityChain;

public class FreeSpaceWarningActivity extends LockableActivity {
	/** Recommended minimum free space on the device, in bytes */
	static final long MINIMUM_SPACE = 200 * 1024 * 1024;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		trace(this, "onCreate()");

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
	public void evtContinue(View view) {
		startAppActivityChain(this);
	}

	public void evtQuit(View view) {
		finish();
	}

//> PRIVATE HELPERS
	private static long asMb(long bytes) {
		return bytes >> 20;
	}
}
