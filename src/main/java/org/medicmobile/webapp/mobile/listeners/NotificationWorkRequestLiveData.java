package org.medicmobile.webapp.mobile.listeners;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class NotificationWorkRequestLiveData {

	private static volatile NotificationWorkRequestLiveData instance;
	private final MutableLiveData<String> requestLiveData = new MutableLiveData<>();

	private NotificationWorkRequestLiveData() {}

	public static NotificationWorkRequestLiveData getInstance() {
		if (instance == null) {
			synchronized (NotificationWorkRequestLiveData.class) {
				if (instance == null) {
					instance = new NotificationWorkRequestLiveData();
				}
			}
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
