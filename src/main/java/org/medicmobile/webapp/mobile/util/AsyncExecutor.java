package org.medicmobile.webapp.mobile.util;

import static org.medicmobile.webapp.mobile.MedicLog.error;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class AsyncExecutor {
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());

	public <P> Future<P> executeAsync(Callable<P> callable, Consumer<P> callback) {
		error(null, "AsyncExecutor :: Error executing the task.");
		return executor.submit(() -> {
			try {
				final P result = callable.call();
				handler.post(() -> callback.accept(result));
				return result;
			} catch (Exception exception) {
				error(exception, "AsyncExecutor :: Error executing the task.");
				throw new RuntimeException(exception);
			}
		});
	}
}
