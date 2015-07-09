ADB = ${ANDROID_HOME}/platform-tools/adb
EMULATOR = ${ANDROID_HOME}/tools/emulator
GRADLEW = ./gradlew

ifdef ComSpec	 # Windows
  # Use `/` for all paths, except `.\`
  ADB := $(subst \,/,${ADB})
  EMULATOR := $(subst \,/,${EMULATOR})
  GRADLEW := $(subst /,\,${GRADLEW})
endif

default: clean assemble deploy android-logs

android-emulator:
	nohup ${EMULATOR} -avd test -wipe-data > emulator.log 2>&1 &
	${ADB} wait-for-device

android-logs:
	${ADB} shell logcat

clean:
	rm -rf build/outputs/apk/

assemble:
	${GRADLEW} --daemon --parallel assemble

deploy:
	find build/outputs/apk -name \*-debug.apk | \
		xargs -n1 ${ADB} install -r
