ADB = ${ANDROID_HOME}/platform-tools/adb
GRADLE = ./gradlew
GRADLE_OPTS = --daemon --parallel
flavor = UnbrandedWebview
abi = x86
KEYTOOL = keytool
OPENSSL = openssl
JAVA = java
XXD = xxd
RM_KEY_OPTS = -i
APKSIGNER = apksigner
RANDOM_HEX = ${XXD} -p -c 256 /dev/urandom

# Public key from Google for signing .pepk files (is the same for all apps in the Play Store)
GOOGLE_ENC_KEY = eb10fe8f7c7c9df715022017b00c6471f8ba8170b13049a11e6c09ffe3056a104a3bbe4ac5a955f4ba4fe93fc8cef27558a3eb9d2a529a2092761fb833b656cd48b9de6a

ifdef ComSpec	 # Windows
  # Use `/` for all paths, except `.\`
  ADB := $(subst \,/,${ADB})
  GRADLE := $(subst /,\,${GRADLE})
endif

default: deploy logs
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

assemble: check-env
	ANDROID_KEYSTORE_PATH=${ANDROID_KEYSTORE_PATH} ANDROID_KEY_ALIAS=${ANDROID_KEY_ALIAS} \
	ANDROID_KEYSTORE_PASSWORD=${ANDROID_KEYSTORE_PASSWORD} ANDROID_KEY_PASSWORD=${ANDROID_KEY_PASSWORD} \
	        ${GRADLE} ${GRADLE_OPTS} assemble${flavor}
assemble-all: check-env
	ANDROID_KEYSTORE_PATH=${ANDROID_KEYSTORE_PATH} ANDROID_KEY_ALIAS=${ANDROID_KEY_ALIAS} \
	ANDROID_KEYSTORE_PASSWORD=${ANDROID_KEYSTORE_PASSWORD} ANDROID_KEY_PASSWORD=${ANDROID_KEY_PASSWORD} \
	        ${GRADLE} ${GRADLE_OPTS} assembleRelease
assemble-all-debug:
	${GRADLE} ${GRADLE_OPTS} assembleDebug

bundle: check-env
	ANDROID_KEYSTORE_PATH=${ANDROID_KEYSTORE_PATH} ANDROID_KEY_ALIAS=${ANDROID_KEY_ALIAS} \
	ANDROID_KEYSTORE_PASSWORD=${ANDROID_KEYSTORE_PASSWORD} ANDROID_KEY_PASSWORD=${ANDROID_KEY_PASSWORD} \
	        ${GRADLE} ${GRADLE_OPTS} bundle${flavor}Release
bundle-all: check-env
	ANDROID_KEYSTORE_PATH=${ANDROID_KEYSTORE_PATH} ANDROID_KEY_ALIAS=${ANDROID_KEY_ALIAS} \
	ANDROID_KEYSTORE_PASSWORD=${ANDROID_KEYSTORE_PASSWORD} ANDROID_KEY_PASSWORD=${ANDROID_KEY_PASSWORD} \
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
	${GRADLE} connectedUnbrandedWebviewDebugAndroidTest \
		-Pabi=${abi} --stacktrace -Pandroid.testInstrumentationRunnerArguments.class=\
	org.medicmobile.webapp.mobile.SettingsDialogActivityTest
test-ui-gamma:
	${GRADLE} connectedMedicmobilegammaWebviewDebugAndroidTest -Pabi=${abi} --stacktrace
test-ui-url:
	DISABLE_APP_URL_VALIDATION=true ${GRADLE} connectedUnbrandedWebviewDebugAndroidTest \
		-Pabi=${abi} --stacktrace -Pandroid.testInstrumentationRunnerArguments.class=\
	org.medicmobile.webapp.mobile.LastUrlTest
test-ui-all: test-ui test-ui-gamma test-ui-url

test-bash-keystore:
	./src/test/bash/bats/bin/bats src/test/bash/test-keystore.bats


#
# "secrets" targets, to setup and unpack keystores
#

# Create the keystore, along with tokens and encrypted files needed
keygen: check-org keysetup check-keystore-exist secrets/secrets-${org}.tar.gz.enc

# Remove the keystore, the pepk file, and the compressed version
keyrm: check-org
	rm ${RM_KEY_OPTS} ${org}.keystore ${org}_private_key.pepk secrets/secrets-${org}.tar.gz

# Remove all: the keystore, the encrypted version, the compressed version and the encrypted version
keyrm-all: check-org
	rm ${RM_KEY_OPTS} secrets/secrets-${org}.tar.gz.enc
	${MAKE} keyrm

# Print info about the keystore
keyprint: check-org check-env
	${KEYTOOL} -list -v -storepass ${ANDROID_KEYSTORE_PASSWORD} -keystore ${org}.keystore

# Print info about the key signature used in the apk release file
keyprint-apk:
	$(if $(shell which $(APKSIGNER)),,$(error "No command '$(APKSIGNER)' in $$PATH"))
	$(eval APK := $(shell find build/outputs/apk -name \*-release.apk | head -n1))
	${APKSIGNER} verify -v --print-certs ${APK}

# Print info about the key signature used in the apk release file
keyprint-bundle:
	$(eval AAB := $(shell find build/outputs/bundle -name \*-release.aab | head -n1))
	${KEYTOOL} -printcert -jarfile ${AAB}

keydec: check-org keysetup check-env
	${OPENSSL} aes-256-cbc -iv ${ANDROID_SECRETS_IV} -K ${ANDROID_SECRETS_KEY} -in secrets/secrets-${org}.tar.gz.enc -out secrets/secrets-${org}.tar.gz -d
	chmod go-rw secrets/secrets-${org}.tar.gz
	tar -xf secrets/secrets-${org}.tar.gz

keysetup:
	$(eval EXEC_CERT_REQUIRED = ${JAVA} ${KEYTOOL} ${OPENSSL} ${XXD})
	$(info Verifying the following executables are in the $$PATH: ${EXEC_CERT_REQUIRED} ...)
	$(foreach exec,$(EXEC_CERT_REQUIRED),\
	        $(if $(shell which $(exec)),,$(error "No command '$(exec)' in $$PATH")))

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
ifdef org
	$(eval ORG_UPPER := $(shell echo $(org) | tr [:lower:] [:upper:]))
endif
ifndef ANDROID_SECRETS_IV
	$(eval VARNAME=ANDROID_SECRETS_IV_${ORG_UPPER})
	$(eval ANDROID_SECRETS_IV := $(shell echo ${${VARNAME}}))
	$(eval VARNAME=ANDROID_SECRETS_KEY_${ORG_UPPER})
	$(eval ANDROID_SECRETS_KEY := $(shell echo ${${VARNAME}}))
	$(eval VARNAME=ANDROID_KEYSTORE_PASSWORD_${ORG_UPPER})
	$(eval ANDROID_KEYSTORE_PASSWORD := $(shell echo ${${VARNAME}}))
	$(eval VARNAME=ANDROID_KEY_PASSWORD_${ORG_UPPER})
	$(eval ANDROID_KEY_PASSWORD := $(shell echo ${${VARNAME}}))
	$(eval VARNAME=ANDROID_KEY_ALIAS_${ORG_UPPER})
	$(eval ANDROID_KEY_ALIAS := $(shell echo ${${VARNAME}}))
	$(eval VARNAME=ANDROID_KEYSTORE_PATH_${ORG_UPPER})
	$(eval ANDROID_KEYSTORE_PATH := $(shell echo ${${VARNAME}}))
endif

check-keystore-exist:
ifneq ("$(wildcard ${org}.keystore ${org}_private_key.pepk)","")
	$(error Files "${org}.keystore" or "${org}_private_key.pepk" already exist. Remove them with "make org=${org} keyrm")
endif

${org}.keystore: check-org
	$(if $(shell which $(XXD)),,$(error "No command '$(XXD)' in $$PATH"))
	$(eval ANDROID_KEYSTORE_PASSWORD := $(shell ${RANDOM_HEX} | head -c 16))
	${KEYTOOL} -genkey -storepass ${ANDROID_KEYSTORE_PASSWORD} -v -keystore ${org}.keystore -alias medicmobile -keyalg RSA -keysize 2048 -validity 9125
	chmod go-rw ${org}.keystore

secrets/secrets-${org}.tar.gz: ${org}.keystore ${org}_private_key.pepk
	tar -czf secrets/secrets-${org}.tar.gz ${org}.keystore ${org}_private_key.pepk
	chmod go-rw secrets/secrets-${org}.tar.gz

${org}_private_key.pepk: check-org pepk.jar
	${JAVA} -jar pepk.jar --keystore=${org}.keystore --alias=medicmobile --keystore-pass=${ANDROID_KEYSTORE_PASSWORD} --output=${org}_private_key.pepk --include-cert --encryptionkey=${GOOGLE_ENC_KEY}
	chmod go-rw ${org}_private_key.pepk

pepk.jar:
	$(info Downloading pepk.jar ...)
	curl https://www.gstatic.com/play-apps-publisher-rapid/signing-tool/prod/pepk.jar -o pepk.jar

secrets/secrets-${org}.tar.gz.enc: secrets/secrets-${org}.tar.gz
	$(eval ANDROID_SECRETS_IV := $(shell ${RANDOM_HEX} | head -c 32))
	$(eval ANDROID_SECRETS_KEY := $(shell ${RANDOM_HEX} | head -c 64))
	$(eval ANDROID_KEYSTORE_PATH := $(org).keystore)
	$(eval ANDROID_KEY_ALIAS := medicmobile)
	${OPENSSL} aes-256-cbc -iv ${ANDROID_SECRETS_IV} -K ${ANDROID_SECRETS_KEY} -in secrets/secrets-${org}.tar.gz -out secrets/secrets-${org}.tar.gz.enc
	chmod go-rw secrets/secrets-${org}.tar.gz.enc
	$(info )
	$(info ###########################################      Secrets!    ###########################################)
	$(info #                                                                                                      #)
	$(info # The following environment variables needs to be added to the CI environment                          #)
	$(info # (Github Actions), and to your local environment if you also want                                     #)
	$(info # to sign APK or AAB files locally:                                                                    #)
	$(info #                                                                                                      #)
	$(info )
	$(info export ANDROID_KEYSTORE_PASSWORD_$(ORG_UPPER)=$(ANDROID_KEYSTORE_PASSWORD))
	$(info export ANDROID_KEY_PASSWORD_$(ORG_UPPER)=$(ANDROID_KEYSTORE_PASSWORD))
	$(info export ANDROID_SECRETS_IV_$(ORG_UPPER)=$(ANDROID_SECRETS_IV))
	$(info export ANDROID_SECRETS_KEY_$(ORG_UPPER)=$(ANDROID_SECRETS_KEY))
	$(info export ANDROID_KEYSTORE_PATH_$(ORG_UPPER)=$(ANDROID_KEYSTORE_PATH))
	$(info export ANDROID_KEY_ALIAS_$(ORG_UPPER)=$(ANDROID_KEY_ALIAS))
	$(info )
	$(info #)
	$(info # The file secrets/secrets-${org}.tar.gz.enc was created and has to be added to the git)
	$(info # repository (don't worry, it's encrypted with some of the keys above).                                #)
	$(info # NOTE: *keep the environment variables secret !!*                                                     #)
	$(info #                                                                                                      #)
	$(info ###########################################  End of Secrets  ###########################################)
	$(info )
