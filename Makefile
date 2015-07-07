ADB = ${ANDROID_HOME}/platform-tools/adb
EMULATOR = ${ANDROID_HOME}/tools/emulator

default: deploy android-logs

android-emulator:
	nohup ${EMULATOR} -avd test -wipe-data > emulator.log 2>&1 &
	${ADB} wait-for-device
android-logs:
	${ADB} shell logcat

deploy:
	rm -rf build/outputs/apk/
	./gradlew --daemon --parallel assemble
	ls build/outputs/apk/*-debug.apk | \
		xargs -n1 ${ADB} install -r
