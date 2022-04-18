package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.error;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TaskRunner {

	private final Executor executor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());

	public interface Callback<R> {
		void onComplete(R result);
	}

	public <R> void executeAsync(Callable<R> callable, Callback<R> callback) {
		executor.execute(() -> {
			try {
				final R result = callable.call();
				handler.post(() -> callback.onComplete(result));
			} catch (Exception exception) {
				error(exception, "TaskRunner :: Error executing the task. Callable=%s, Callback=%s", callable, callback);
			}
		});
	}
}
