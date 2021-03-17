package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

public class LockScreen extends Activity {

	private static final String X_TASK = "LockScreen.TASK";
	private static final String X_FIRST_ENTRY = "LockScreen.FIRST_ENTRY";

	enum Task {
		UNLOCK(R.string.txtUnlockPrompt, false),
		CONFIRM_OLD(R.string.txtOldCodePrompt, true),
		ENTER_NEW(R.string.txtNewCodePrompt, true),
		CONFIRM_NEW(R.string.txtNewCodeConfirmPrompt, true);

		final int title;
		final boolean allowBack;

		Task(int title, boolean allowBack) {
			this.title = title;
			this.allowBack = allowBack;
		}
	}

	private Task task;

//> Activity OVERRIDES
	@Override public void onBackPressed() { if(task.allowBack) super.onBackPressed(); }

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		trace(this, "onCreate()");

		task = Task.valueOf(getIntent().getStringExtra(X_TASK));

		setContentView(R.layout.pin_entry);

		setTitle(task.title);

		Button btn = (Button) findViewById(R.id.btnConfirmPinEntry);
		btn.setOnClickListener(clickListenerFor(task));

		final EditText txtPin = (EditText) findViewById(R.id.txtPinEntry);

		// Ignore all input on the PIN field, but allow it to be styled
		// as if it were editable.
		txtPin.setKeyListener(null);

		final ViewGroup group = (ViewGroup) findViewById(R.id.divButtons);
		int i = group.getChildCount();
		OnClickListener buttonListener = new OnClickListener() {
			@Override public void onClick(View v) {
				String newText = txtPin.getText() + ((Button) v).getText().toString();
				txtPin.setText(newText);
				txtPin.setSelection(newText.length());
			}
		};
		while(--i >= 0) {
			Button b = (Button) group.getChildAt(i);
			b.setOnClickListener(buttonListener);
		}

		Button btnBackspace = (Button) findViewById(R.id.btnBackspace);
		btnBackspace.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				CharSequence text = txtPin.getText();
				if(text.length() > 0) text = text.subSequence(0, text.length()-1);
				txtPin.setText(text);
				txtPin.setSelection(text.length());
			}
		});
	}

//> UI SETUP
	@SuppressWarnings({ "PMD.StdCyclomaticComplexity", "PMD.ModifiedCyclomaticComplexity" })
	private OnClickListener clickListenerFor(Task t) {
		switch(t) {
			case UNLOCK:
				return new OnClickListener() {
					public void onClick(View v) {
						if(confirmCodeEnteredCorrectly()) dismissWithMessage(R.string.tstUnlock_success);
						else rejectCode(R.string.tstUnlock_codeRejected);
					}
				};
			case CONFIRM_OLD:
				return new OnClickListener() {
					@Override public void onClick(View v) {
						if(confirmCodeEnteredCorrectly()) {
							showFor(LockScreen.this, Task.ENTER_NEW);
							dismissWithMessage(R.string.tstUnlock_oldAccepted);
						} else rejectCode(R.string.tstUnlock_codeRejected);
					}
				};
			case ENTER_NEW:
				return new OnClickListener() {
					@Override public void onClick(View v) {
						Intent i = getIntent(LockScreen.this, Task.CONFIRM_NEW);
						i.putExtra(X_FIRST_ENTRY, enteredText());
						startActivity(i);
						dismissWithMessage(R.string.tstUnlock_newAccepted);
					}
				};
			case CONFIRM_NEW:
				return new OnClickListener() {
					@Override public void onClick(View v) {
						String firstEntry = getIntent().getStringExtra(X_FIRST_ENTRY);
						if(firstEntry.equals(enteredText())) {
							try {
								SettingsStore.in(LockScreen.this).updateWithUnlockCode(firstEntry);
								dismissWithMessage(R.string.tstUnlock_codeChanged);
							} catch(SettingsException ex) {
								warn(ex, "Failed to save new unlock code.");
								toast(R.string.tstUnlock_failed);
							}
						} else rejectCode(R.string.tstUnlock_confirmFailed);
					}
				};
			default: throw new IllegalArgumentException(String.valueOf(task));
		}
	}

//> PRIVATE HELPERS
	private boolean confirmCodeEnteredCorrectly() {
		String unlockCode = SettingsStore.in(this).getUnlockCode();
		return enteredText().equals(unlockCode);
	}

	private String enteredText() {
		return txtPinEntry().getText().toString();
	}

	private void rejectCode(int textId) {
		txtPinEntry().setText("");
		toast(textId);
	}

	private void dismissWithMessage(int textId) {
		toast(textId);
		finish();
	}

	private void toast(int textId) { Toast.makeText(this, textId, Toast.LENGTH_SHORT).show(); }

	private TextView txtPinEntry() {
		return (TextView) findViewById(R.id.txtPinEntry);
	}

//> STATIC UTILS
	static boolean isCodeSet(Activity a) {
		String unlockCode = SettingsStore.in(a).getUnlockCode();
		return unlockCode != null && !unlockCode.isEmpty();
	}

	static void showFrom(Activity caller) {
		showFor(caller, Task.UNLOCK);
	}

	static void showFor(Activity caller, Task task) {
		caller.startActivity(getIntent(caller, task));
	}

//> STATIC HELPERS
	private static Intent getIntent(Activity caller, Task task) {
		Intent i = new Intent(caller, LockScreen.class);
		i.putExtra(X_TASK, task.toString());
		return i;
	}
}

abstract class LockableActivity extends Activity {
	private boolean suppressLockScreen;

	@Override public void finish() {
		suppressLockScreen = true;
		super.finish();
	}

	@Override public void onPause() {
		// Handle the lock screen here, as doing it in onResume() means
		// that the screen we're trying to block is displayed
		// momentarily when returning to the activity.
		//
		// N.B. if landscape mode were enabled for any activities, then
		// onPause() will be fired when the screen rotates.  We would
		// have to detect this in onConfigurationChanged() and ignore.

		if(!suppressLockScreen && LockScreen.isCodeSet(this)) LockScreen.showFrom(this);

		super.onPause();
	}

	protected void changeCode() {
		suppressLockScreen = true;
		LockScreen.showFor(this, LockScreen.isCodeSet(this) ?
				LockScreen.Task.CONFIRM_OLD :
				LockScreen.Task.ENTER_NEW);
	}
}
