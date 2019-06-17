ADB = ${ANDROID_HOME}/platform-tools/adb
GRADLEW = ./gradlew
flavour = Unbranded

ifdef ComSpec	 # Windows
  # Use `/` for all paths, except `.\`
  ADB := $(subst \,/,${ADB})
  GRADLEW := $(subst /,\,${GRADLEW})
endif

default: deploy-flavour logs
branded: clean-bundles bundle-all deploy-all logs
branded-debug: clean-bundles bundle-all-debug deploy-all logs
clean: clean-bundles

logs:
	${ADB} logcat MedicMobile:V AndroidRuntime:E '*:S' | tee android.log

deploy-flavour:
	${GRADLEW} --daemon --parallel install${flavour}Debug

clean-bundles:
	rm -rf build/outputs/bundle/
bundle-all:
	${GRADLEW} --daemon --parallel bundle
bundle-all-debug:
	${GRADLEW} --daemon --parallel bundleDebug
deploy-all:
	find build/outputs/bundle -name \*-debug.aab | \
		xargs -n1 ${ADB} install -r
uninstall-all:
	${GRADLEW} uninstallAll
url-tester:
	DISABLE_APP_URL_VALIDATION=true ${GRADLEW} --daemon --parallel installUnbrandedDebug
uninstall:
	adb uninstall org.medicmobile.webapp.mobile

test:
	${GRADLEW} androidCheck lintUnbrandedDebug test

travis:
	${GRADLEW} assembleRelease
