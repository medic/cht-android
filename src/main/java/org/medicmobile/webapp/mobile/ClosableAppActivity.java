package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Base class for activities that when back is pressed
 * it closes the app instead of move backward through
 * the history of screens, or prevent the user to either
 * close or go back in the activities stack.
 *
 * By default when the user press back the app is exited.
 *
 * The opposite action can be configured: the app
 * not closing nor the activity when the user press
 * back one or more times: to do so the `Intent` used to launch
 * the activity needs to be set with the extended data "isClosable"
 * to `false` (calling `intent.putExtra("isClosable", false)`).
 * No message is shown to the user when back is pressed unless
 * the extended data "backPressedMessage" is also set with the
 * desired message string.
 */
public abstract class ClosableAppActivity extends Activity {

	private boolean isClosable = true;			// if true, back pressing close the app
	private String backPressedMessage = null;	// Customized message to show when back is pressed
												// and the app activity is not closable

	@Override public void onBackPressed() {
		if (isClosable) {
			finishAffinity();
		} else {
			// Back pressed events won't close the app nor close the activity
			if (backPressedMessage != null) {
				Toast.makeText(this, backPressedMessage, Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent i = getIntent();
		isClosable = i.getBooleanExtra("isClosable", true);
		backPressedMessage = i.getStringExtra("backPressedMessage");
	}
}
