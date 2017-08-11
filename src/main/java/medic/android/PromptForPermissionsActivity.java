package medic.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import org.medicmobile.webapp.mobile.R;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

/**
 * To support Android 6.0+ (marshmallow), we must request SMS permissions at
 * runtime as well as in {@code AndroidManifest.xml}.
 * @see https://developer.android.com/intl/ru/about/versions/marshmallow/android-6.0-changes.html#behavior-runtime-permissions
 *
 * TODO this class was copy/pasted from medic-gateway.  It should be pulled into
 * a separate lib so that both projects can share the same source.
 */
public abstract class PromptForPermissionsActivity extends Activity implements ActivityCompat.OnRequestPermissionsResultCallback {
	private static final String X_IS_DEMAND = "isDemand";
	private static final String X_PERMISSIONS_TYPE = "permissionsType";

	private boolean isDemand;
	private boolean deniedBefore;
	private int permissionsRequestType;

//> API
	protected abstract boolean refuseToFunctionWithoutPermissions();
	protected abstract Object[][] getPermissionRequests();
	protected abstract Class<? extends Activity> getNextActivityClass();

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		isDemand = getIntent().getBooleanExtra(X_IS_DEMAND, false);

		trace(this, "onCreate() :: isDemand=%s, permissionsRequestType=%s", isDemand, permissionsRequestType);

		setContentView(R.layout.prompt_for_permissions);

		int promptTextId;
		if(isDemand) {
			promptTextId = R.string.txtDemandPermissions;
		} else {
			permissionsRequestType = getIntent().getIntExtra(X_PERMISSIONS_TYPE, 0);
			promptTextId = (int) getPermissionRequests()[permissionsRequestType][0];
			makePermissionRequest();
		}

		String appName = getResources().getString(R.string.app_name);
		CharSequence text = Html.fromHtml(getResources().getString(promptTextId, appName));
		((TextView) findViewById(R.id.txtPermissionsPrompt)).setText(text);
	}

//> EVENT HANDLERS
	public void btnOk_onClick(View v) {
		if(isDemand) {
			// open app manager for this app
			Intent i = new Intent(ACTION_APPLICATION_DETAILS_SETTINGS,
					Uri.fromParts("package", getPackageName(), null));
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);

			finish();
		} else makePermissionRequest();
	}

	@SuppressWarnings("PMD.UseVarargs")
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		boolean allGranted = true;
		for(int res : grantResults) allGranted &= res == PERMISSION_GRANTED;

		if(allGranted) {
			nextActivity(this, permissionsRequestType + 1);
		} else if(refuseToFunctionWithoutPermissions()) {
			// For some flavours, we don't want to give people the option to use the app without the
			// correct permissions.  If the permission is not granted, re-request the same.
			if(canShowPromptFor(this, permissionsRequestType)) { // NOPMD
				// Don't do anything - the user can re-read the on-screen advice.
			} else {
				// The user has checked the "don't ask me again"/"never allow" box (TODO which one?), so we have to step things up.
				startActivity(demandPermissions(this));
				finish();
			}
		} else {
			if(!deniedBefore && canShowPromptFor(this, permissionsRequestType)) {
				// Allow user to read the advice on the screen
				deniedBefore = true;
			} else nextActivity(this, permissionsRequestType + 1);
		}
	}

	protected void startPermissionsRequestChain(Activity a) {
		nextActivity(a, 0);
	}

	private void nextActivity(Activity a, int firstPermissionToConsider) {
		trace(a, "nextActivity() :: %s", firstPermissionToConsider);

		Intent next = null;

		for(int p=firstPermissionToConsider; p<getPermissionRequests().length; ++p) {
			if(!hasRequiredPermissions(a, p)) {
				next = requestPermission(a, p);
				break;
			}
		}

		if(next == null) next = new Intent(a, getNextActivityClass());

		trace(a, "nextActivity() :: Should start activity with intent: %s", next);

		a.startActivity(next);
		a.finish();
	}

	private boolean canShowPromptFor(Activity a, int permissionsRequestType) {
		trace(a, "canShowPromptFor() p=%s", permissionsRequestType);
		for(String p : getPermissions(permissionsRequestType)) {
			boolean shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(a, p);
			trace(a, "canShowPromptFor() can %s? %s", p, shouldShow);
			if(!shouldShow) return false;
		}
		return true;
	}

	private boolean hasRequiredPermissions(Activity a, int permissionsRequestType) {
		trace(a, "hasRequiredPermissions() :: %s", permissionsRequestType);
		for(String p : getPermissions(permissionsRequestType))
			if(ContextCompat.checkSelfPermission(a, p) != PERMISSION_GRANTED)
				return false;
		return true;
	}

	private String[] getPermissions(int permissionsRequestType) {
		return (String[]) getPermissionRequests()[permissionsRequestType][1];
	}

//> PRIVATE HELPERS
	private void makePermissionRequest() {
		ActivityCompat.requestPermissions(this, getPermissions(permissionsRequestType), 0);
	}

	private Intent requestPermission(Activity a, int permissionsRequestType) {
		trace(a, "requestPermission() :: p=%s", permissionsRequestType);
		Intent i = new Intent(a, getClass());
		i.putExtra(X_PERMISSIONS_TYPE, permissionsRequestType);
		i.putExtra(X_IS_DEMAND, false);
		return i;
	}

	private Intent demandPermissions(Activity a) {
		trace(a, "demandPermission()");
		Intent i = new Intent(a, getClass());
		i.putExtra(X_IS_DEMAND, true);
		return i;
	}
}
