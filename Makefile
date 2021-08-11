ADB = ${ANDROID_HOME}/platform-tools/adb
GRADLE = ./gradlew
GRADLE_OPTS = --daemon --parallel
flavor = UnbrandedWebview
abi = x86
KEYTOOL = keytool
OPENSSL = openssl
RM_KEY_OPTS = -i

ifdef ComSpec	 # Windows
  # Use `/` for all paths, except `.\`
  ADB := $(subst \,/,${ADB})
  GRADLE := $(subst /,\,${GRADLE})
endif

default: deploy logs
branded: clean-apks assemble-all deploy-all logs
branded-debug: clean-apks assemble-all-debug deploy-all logs
xwalk: deploy-xwalk logs

logs:
	${ADB} logcat MedicMobile:V AndroidRuntime:E '*:S' | tee android.log

deploy:
	${GRADLE} ${GRADLE_OPTS} install${flavor}Debug
deploy-xwalk:
	${GRADLE} ${GRADLE_OPTS} installUnbrandedXwalkDebug
deploy-all:
	find build/outputs/apk -name \*-debug.apk | \
		xargs -n1 ${ADB} install -r

clean:
	${GRADLE} clean
clean-apks:
	rm -rf build/outputs/apk/

assemble:
	${GRADLE} ${GRADLE_OPTS} assemble${flavor}
assemble-all:
	${GRADLE} ${GRADLE_OPTS} assembleRelease
assemble-all-debug:
	${GRADLE} ${GRADLE_OPTS} assembleDebug

bundle:
	${GRADLE} ${GRADLE_OPTS} bundle${flavor}Release
bundle-all:
	${GRADLE} ${GRADLE_OPTS} bundleRelease

uninstall-all:
	${GRADLE} uninstallAll

url-tester:
	DISABLE_APP_URL_VALIDATION=true ${GRADLE} ${GRADLE_OPTS} install${flavor}Debug

uninstall:
	adb uninstall org.medicmobile.webapp.mobile

lint:
	${GRADLE} ${GRADLE_OPTS} androidCheck lint${flavor}Debug
test: lint
	${GRADLE} ${GRADLE_OPTS} test
test-ui:
	${GRADLE} connectedUnbrandedWebviewDebugAndroidTest -Pabi=${abi} --stacktrace
test-ui-gamma:
	${GRADLE} connectedMedicmobilegammaWebviewDebugAndroidTest -Pabi=${abi} --


#
# "secrets" targets, to setup and unpack keystores
#

# Generate keystore
keystore: check-org ${org}.keystore

# Remove the keystore, the encrypted version and the compressed version
keyrm: check-org
	rm ${RM_KEY_OPTS} ${org}.keystore secrets/secrets-${org}.tar.gz secrets/secrets-${org}.tar.gz.enc

# Remove the keystore and the compressed version, leaving only the encrypted version
keyclean: check-org
	rm ${RM_KEY_OPTS} ${org}.keystore secrets/secrets-${org}.tar.gz

keyprint: check-org check-env
	${KEYTOOL} -list -v -storepass ${ANDROID_KEYSTORE_PASSWORD} -keystore ${org}.keystore

keygen: check-org secrets/secrets-${org}.tar.gz.enc

keydec: check-org check-env
	${OPENSSL} aes-256-cbc -iv ${ANDROID_SECRETS_IV} -K ${ANDROID_SECRETS_KEY} -in secrets/secrets-${org}.tar.gz.enc -out secrets/secrets-${org}.tar.gz -d
	${MAKE} keyunpack

keyunpack: check-org
	tar -xf secrets/secrets-${org}.tar.gz

#
# Intermediate targets for "secrets", don't use them
#

check-org:
ifndef org
	second_argument := $(word 2, $(MAKECMDGOALS) )
	$(error "org" name not set. Try 'make org=name $(filter-out $@, $(MAKECMDGOALS))')
endif
ifeq ($(org),name)
	$(error "org" cannot be equal to "name", it was just an example :S)
endif
ifdef org
	$(eval ORG_UPPER := $(shell echo $(org) | tr [:lower:] [:upper:]))
endif

check-env:
ifndef ANDROID_SECRETS_IV
	$(eval VARNAME=ANDROID_SECRETS_IV_${ORG_UPPER})
	$(eval ANDROID_SECRETS_IV := $(shell echo ${${VARNAME}}))
	$(eval VARNAME=ANDROID_SECRETS_KEY_${ORG_UPPER})
	$(eval ANDROID_SECRETS_KEY := $(shell echo ${${VARNAME}}))
	$(eval VARNAME=ANDROID_KEYSTORE_PASSWORD_${ORG_UPPER})
	$(eval ANDROID_KEYSTORE_PASSWORD := $(shell echo ${${VARNAME}}))
endif

${org}.keystore: check-org
	$(eval ANDROID_KEYSTORE_PASSWORD := $(shell base16 /dev/urandom | head -n 1 -c 16))
	${KEYTOOL} -genkey -storepass ${ANDROID_KEYSTORE_PASSWORD} -v -keystore ${org}.keystore -alias medicmobile -keyalg RSA -keysize 2048 -validity 9125
	chmod go-rw ${org}.keystore

secrets/secrets-${org}.tar.gz: ${org}.keystore
	tar -czf secrets/secrets-${org}.tar.gz ${org}.keystore
	chmod go-rw secrets/secrets-${org}.tar.gz

secrets/secrets-${org}.tar.gz.enc: secrets/secrets-${org}.tar.gz
	$(eval ANDROID_SECRETS_IV := $(shell base16 /dev/urandom | head -n 1 -c 32))
	$(eval ANDROID_SECRETS_KEY := $(shell base16 /dev/urandom | head -n 1 -c 64))
	${OPENSSL} aes-256-cbc -iv ${ANDROID_SECRETS_IV} -K ${ANDROID_SECRETS_KEY} -in secrets/secrets-${org}.tar.gz -out secrets/secrets-${org}.tar.gz.enc
	chmod go-rw secrets/secrets-${org}.tar.gz.enc
	$(info )
	$(info ######################################      Secrets!    ######################################)
	$(info #                                                                                            #)
	$(info # The following environment variables needs to be added to the CI                            #)
	$(info # environment (Github Actions):)
	$(info )
	$(info ANDROID_KEYSTORE_PASSWORD_$(ORG_UPPER)=$(ANDROID_KEYSTORE_PASSWORD))
	$(info ANDROID_KEY_PASSWORD_$(ORG_UPPER)=$(ANDROID_KEYSTORE_PASSWORD))
	$(info ANDROID_SECRETS_IV_$(ORG_UPPER)=$(ANDROID_SECRETS_IV))
	$(info ANDROID_SECRETS_KEY_$(ORG_UPPER)=$(ANDROID_SECRETS_KEY))
	$(info )
	$(info #)
	$(info # If you also want to sign APK or AAB files locally, store them as)
	$(info # local variables without the _$(ORG_UPPER) prefix as follow (and *keep them secret !!*):)
	$(info #)
	$(info )
	$(info export ANDROID_KEYSTORE_PASSWORD=$(ANDROID_KEYSTORE_PASSWORD))
	$(info export ANDROID_KEY_PASSWORD=$(ANDROID_KEYSTORE_PASSWORD))
	$(info export ANDROID_SECRETS_IV=$(ANDROID_SECRETS_IV))
	$(info export ANDROID_SECRETS_KEY=$(ANDROID_SECRETS_KEY))
	$(info )
	$(info export ANDROID_KEYSTORE_PATH=$(org).keystore)
	$(info export ANDROID_KEY_ALIAS=medicmobile)
	$(info )
	$(info #                                                                                            #)
	$(info #                                                                                            #)
	$(info ######################################  End of Secrets  ######################################)
	$(info )
