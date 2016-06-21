ADB = ${ANDROID_HOME}/platform-tools/adb
EMULATOR = ${ANDROID_HOME}/tools/emulator
GRADLEW = ./gradlew

ifdef ComSpec	 # Windows
  # Use `/` for all paths, except `.\`
  ADB := $(subst \,/,${ADB})
  EMULATOR := $(subst \,/,${EMULATOR})
  GRADLEW := $(subst /,\,${GRADLEW})
endif

default: deploy-unbranded android-logs
branded: clean-apks assemble-all deploy-all android-logs
branded-debug: clean-apks assemble-all-debug deploy-all android-logs
clean: clean-apks

android-emulator:
	nohup ${EMULATOR} -avd test -wipe-data > emulator.log 2>&1 &
	${ADB} wait-for-device

android-logs:
	${ADB} logcat MedicMobile:V AndroidRuntime:E '*:S' | tee android.log

deploy-unbranded:
	${GRADLEW} --daemon --parallel installUnbrandedDebug

kill:
	pkill -9 emulator64-arm

clean-apks:
	rm -rf build/outputs/apk/
assemble-all:
	${GRADLEW} --daemon --parallel assemble
assemble-all-debug:
	${GRADLEW} --daemon --parallel assembleDebug
deploy-all:
	find build/outputs/apk -name \*-debug.apk | \
		xargs -n1 ${ADB} install -r
url-tester:
	DISABLE_APP_URL_VALIDATION=true ${GRADLEW} --daemon --parallel installUnbrandedDebug
