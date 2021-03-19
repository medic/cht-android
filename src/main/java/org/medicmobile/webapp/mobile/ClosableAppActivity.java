package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

/**
 * Base class for activities that when back is pressed
 * two times the app closes.
 *
 * When the user press back for the first time, a message
 * defined in the `R.string.backToExit` string is shown
 * explaining that it has to press back one more time. The
 * message can be customized in the intent with the extended
 * data "backPressedMessage"
 * (calling `intent.putExtra("backPressedMessage", "...")`).
 *
 * Also the opposite action can be configured: the app
 * not closing nor the activity when the user press
 * back one or more times: to do so the `Intent` used to launch
 * the activity needs to be set with the extended data "isClosable"
 * to `false` (calling `intent.putExtra("isClosable", false)`).
 * No message is shown to the user when back is pressed unless
 * the extended data "backPressedMessage" is also set with the
 * desired message.
 */
public abstract class ClosableAppActivity extends Activity {

	private static final int WAIT_MILLIS = 2 * 1000;    // Back need to be pressed 2 times before this time

	private boolean closeableApp = true;                // if true, back pressing 2 times close the app
	private boolean backToExitPressedOnce = false;      // whether back was pressed the last WAIT_MILLIS
	private String backPressedMessage = null;			// Customized message to show when back is pressed

	@Override public void onBackPressed() {
		if (closeableApp) {
			if (backToExitPressedOnce) {
				finishAffinity();
				return;
			}
			backToExitPressedOnce = true;

			toastMessage();

			new Handler().postDelayed(new Runnable() {
				@Override public void run() {
					// After WAIT_MILLIS clean the back pressed history
					backToExitPressedOnce = false;
				}
			}, WAIT_MILLIS);
		} else {
			// Back pressed events won't close the app nor close the activity
			toastMessage();
		}
	}

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent i = getIntent();
		closeableApp = i.getBooleanExtra("isClosable", true);	// not closable (the default value is true)
		backPressedMessage = i.getStringExtra("backPressedMessage");	// default null
	}

	private void toastMessage() {
		if (closeableApp) {
			if (backPressedMessage != null) {
				Toast.makeText(this, backPressedMessage, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(this, R.string.backToExit, Toast.LENGTH_SHORT).show();
			}
		} else if (backPressedMessage != null) {
			Toast.makeText(this, backPressedMessage, Toast.LENGTH_SHORT).show();
		}
	}
}
