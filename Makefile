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

android-emulator:
	nohup ${EMULATOR} -avd test -wipe-data > emulator.log 2>&1 &
	${ADB} wait-for-device

android-logs:
	${ADB} shell logcat

deploy-unbranded:
	${GRADLEW} --daemon --parallel installUnbrandedDebug

clean-apks:
	rm -rf build/outputs/apk/
assemble-all:
	${GRADLEW} --daemon --parallel assemble
deploy-all:
	find build/outputs/apk -name \*-debug.apk | \
		xargs -n1 ${ADB} install -r
