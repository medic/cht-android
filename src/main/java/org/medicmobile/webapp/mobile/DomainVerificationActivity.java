package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.trace;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import java.util.Map;

public class DomainVerificationActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		trace(this, "onCreate()");

		if (!this.checkIfDomainsAreVerified()) {
			setContentView(R.layout.request_app_domain_association);
		} else {
			finish();
		}
	}

	private boolean checkIfDomainsAreVerified() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			Context context = getApplicationContext();
			DomainVerificationManager manager =
				context.getSystemService(DomainVerificationManager.class);
			try {
				DomainVerificationUserState userState =
					manager.getDomainVerificationUserState(context.getPackageName());

				Map<String, Integer> hostToStateMap = userState.getHostToStateMap();

				for (String key : hostToStateMap.keySet()) {
					Integer stateValue = hostToStateMap.get(key);

					if (stateValue != DomainVerificationUserState.DOMAIN_STATE_VERIFIED && stateValue != DomainVerificationUserState.DOMAIN_STATE_SELECTED) {
						return false;
					}
				}
			} catch (PackageManager.NameNotFoundException e) {
				return true;
			}
		}
		return true;
	}

	public void onClickOk(View view) {
		trace(this, "DomainVerificationActivity :: User agreed with prominent disclosure message.");
		Intent intent = new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + this.getPackageName()));
		this.startActivity(intent);
		finish();
	}

	public void onClickNegative(View view) {
		trace(this, "DomainVerificationActivity :: User disagreed with prominent disclosure message.");
		finish();
	}
}
