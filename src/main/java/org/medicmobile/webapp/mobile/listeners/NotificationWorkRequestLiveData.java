package org.medicmobile.webapp.mobile.listeners;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class NotificationWorkRequestLiveData {

	private static NotificationWorkRequestLiveData instance;
	private final MutableLiveData<String> requestLiveData = new MutableLiveData<>();

	private NotificationWorkRequestLiveData() {}

	public static synchronized NotificationWorkRequestLiveData getInstance() {
		if (instance == null) {
			instance = new NotificationWorkRequestLiveData();
		}
		return instance;
	}

	public LiveData<String> getRequest() {
		return requestLiveData;
	}

	public void sendRequest(String javascript) {
		requestLiveData.postValue(javascript);
	}

	public void resetRequest() {
		requestLiveData.postValue(null);
	}
}
