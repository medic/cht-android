ADB = ${ANDROID_HOME}/platform-tools/adb
GRADLEW = ./gradlew
flavour = Unbranded

ifdef ComSpec	 # Windows
  # Use `/` for all paths, except `.\`
  ADB := $(subst \,/,${ADB})
  GRADLEW := $(subst /,\,${GRADLEW})
endif

default: deploy-flavour logs
branded: clean-apks assemble-all deploy-all logs
branded-debug: clean-apks assemble-all-debug deploy-all logs
clean: clean-apks

logs:
	${ADB} logcat MedicMobile:V AndroidRuntime:E '*:S' | tee android.log

deploy-flavour:
	${GRADLEW} --daemon --parallel install${flavour}Debug

clean-apks:
	rm -rf build/outputs/apk/
assemble-all:
	${GRADLEW} --daemon --parallel assemble
assemble-all-debug:
	${GRADLEW} --daemon --parallel assembleDebug
deploy-all:
	find build/outputs/apk -name \*-debug.apk | \
		xargs -n1 ${ADB} install -r
uninstall-all:
	${GRADLEW} uninstallAll
url-tester:
	DISABLE_APP_URL_VALIDATION=true ${GRADLEW} --daemon --parallel installUnbrandedDebug
uninstall:
	adb uninstall org.medicmobile.webapp.mobile
test:
	${GRADLEW} androidCheck lintUnbrandedDebug test
