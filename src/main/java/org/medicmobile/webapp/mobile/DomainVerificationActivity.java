package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.trace;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;


public class DomainVerificationActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		trace(this, "onCreate()");

		setContentView(R.layout.request_app_domain_association);

		String appName = getResources().getString(R.string.app_name);
		String title = getResources().getString(R.string.domainAppAssociationTitle);
		TextView field = findViewById(R.id.domainAppAssociationTitleText);
		field.setText(String.format(title, appName));
	}

	public void onClickOk(View view) {
		trace(this, "DomainVerificationActivity :: User agreed with prominent disclosure message.");
		@SuppressLint("InlinedApi") Intent intent = new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + this.getPackageName()));
		this.startActivity(intent);
		finish();
	}

	public void onClickNegative(View view) {
		trace(this, "DomainVerificationActivity :: User disagreed with prominent disclosure message.");
		finish();
	}
}
