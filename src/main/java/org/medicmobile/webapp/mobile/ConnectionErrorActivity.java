package org.medicmobile.webapp.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import static android.view.View.VISIBLE;
import static android.view.View.GONE;
import static org.medicmobile.webapp.mobile.MedicLog.trace;


/**
 * Activity displayed to catch connection errors and give the user
 * the chance to retry.
 *
 * NOTE: it only catch connection errors when the web container
 * tries to load a new page or reload the current one.
 * Errors when the webapp does ajax calls are caught by
 * the webapp itself.
 */
public class ConnectionErrorActivity extends ClosableAppActivity {

	String connErrorInfo;	// Error code and description

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.connection_error);

		// If a page load finished and the activity is still spinning -> close
		BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
			@Override public void onReceive(Context context, Intent intent) {
				if ("onPageFinished".equals(intent.getAction()) && isSpinning()) {
					unregisterReceiver(this);
					finish();
				}
			}
		};
		registerReceiver(broadcastReceiver, new IntentFilter("onPageFinished"));

		Intent i = getIntent();
		connErrorInfo = i.getStringExtra("connErrorInfo");
	}

	@Override protected void onNewIntent(Intent intent) {
		showMessageLayout();
	}

	// Retry connection
	public void onClickRetry(View view) {
		trace(this, "onClickRetry()");
		sendBroadcast(new Intent("retryConnection"));
		showSpinnerLayout();
	}

	// Show more info about the connection error
	public void onClickMoreInfo(View view) {
		trace(this, "onClickMoreInfo()");
		AlertDialogUtils.show(this, getString(R.string.btnMoreInfo), connErrorInfo);
	}

	// Toggle layout components: hide the message layout
	// and display the spinner layout
	private void showSpinnerLayout() {
		setLayoutsVisibility(GONE, VISIBLE);
	}

	// Toggle layout components: hide the spinner layout
	// and display the message layout
	private void showMessageLayout() {
		setLayoutsVisibility(VISIBLE, GONE);
	}

	// Set visibility for the Internet connection error layout
	// and the spinner layout
	private void setLayoutsVisibility(int messageLayoutVisibility, int spinnerVisibility) {
		View messageLayout = getMessageLayout();
		messageLayout.setVisibility(messageLayoutVisibility);
		View spinnerLayout = getSpinnerLayout();
		spinnerLayout.setVisibility(spinnerVisibility);
	}

	private View getMessageLayout() {
		return findViewById(R.id.connErrorMessageLayout);
	}

	private View getSpinnerLayout() {
		return findViewById(R.id.connErrorSpinnerLayout);
	}

	private boolean isSpinning() {
		return getSpinnerLayout().getVisibility() == VISIBLE;
	}
}
