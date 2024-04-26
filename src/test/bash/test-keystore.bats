setup() {
  load 'test_helper/bats-support/load'
  load 'test_helper/bats-assert/load'
  # get the containing directory of this file
  # use $BATS_TEST_FILENAME instead of ${BASH_SOURCE[0]} or $0,
  # as those will point to the bats executable's location or the preprocessed file respectively
  DIR="$( cd "$( dirname "$BATS_TEST_FILENAME" )" >/dev/null 2>&1 && pwd )"
  # make executables in src/ visible to PATH
  PATH="$DIR/../src:$PATH"
}

teardown() {
  make RM_KEY_OPTS="-f" org=test keyrm-all
}

@test "can execute make key* targets" {
  run make keysetup
  assert_success
  assert_output --partial "keysetup' is up to date."
}

@test "can't execute make key* targets when one of the tools is missing" {
  run make XXD=notxxd keysetup
  assert_failure 2
  refute_output --partial "keysetup' is up to date."
  assert_output --partial "\"No command 'notxxd' in \$PATH\".  Stop."
}

@test "can't execute key* targets without \"org\" argument when org is required" {
  run make keygen
  assert_failure 2
  assert_output --partial "\"org\" name not set. Try 'make org=name keygen'.  Stop."
  refute_output --partial "Verifying the following executables"
}

@test "can generate and encrypt keystore" {
  run bash -c 'yes | make org=test keygen'
  assert_success
  refute_output --partial "\"org\" name not set. Try 'make org=name keygen'.  Stop."
  assert_output --partial "Verifying the following executables"
  assert_output --partial "keytool -genkey -storepass"
  assert_output --partial "Secrets!"
  assert [ -e './test.keystore' ]
  assert [ -e './test_private_key.pepk' ]
  assert [ -e './secrets/secrets-test.tar.gz' ]
  assert [ -e './secrets/secrets-test.tar.gz.enc' ]
  ANDROID_KEYSTORE_PASSWORD_TEST=$(echo $output | grep ANDROID_KEYSTORE_PASSWORD_TEST | awk 'BEGIN { FS="=" } {print $2}')
  assert [ ! -z "$ANDROID_KEYSTORE_PASSWORD_TEST" ]
}

@test "can remove all the generated files" {
  run bash -c 'yes | make org=test keygen'
  make RM_KEY_OPTS="-f" org=test keyrm-all
  assert_success
  assert [ ! -e './test.keystore' ]
  assert [ ! -e './test_private_key.pepk' ]
  assert [ ! -e './secrets/secrets-test.tar.gz' ]
  assert [ ! -e './secrets/secrets-test.tar.gz.enc' ]
}

@test "can remove all the generated files except the encrypted file" {
  run bash -c 'yes | make org=test keygen'
  make RM_KEY_OPTS="-f" org=test keyrm
  assert_success
  assert [ ! -e './test.keystore' ]
  assert [ ! -e './test_private_key.pepk' ]
  assert [ ! -e './secrets/secrets-test.tar.gz' ]
  assert [ -e './secrets/secrets-test.tar.gz.enc' ]
}

@test "can't regenerate keystore without removing the previous one" {
  # First attempt successes
  run bash -c 'yes | make org=test keygen'
  assert_success
  # Second one fails
  run bash -c 'yes | make org=test keygen'
  assert_failure 2
  assert_output --partial "Files \"test.keystore\" or \"test_private_key.pepk\" already exist."
}

@test "can't decrypt keystore when having wrong environment variable keys set" {
  # Create a keystore
  run bash -c 'yes | make org=test keygen'
  # Removed the unencrypted files
  make RM_KEY_OPTS="-f" org=test keyrm
  # Set the environment variables needed to decrypt but with wrong keys
  export ANDROID_KEYSTORE_PASSWORD_TEST="1232"
  export ANDROID_KEY_PASSWORD_TEST="abc"
  export ANDROID_SECRETS_IV_TEST="1234abc"
  export ANDROID_SECRETS_KEY_TEST="111222"
  # Now trying to decrypt without the right env sets fails
  run make org=test keydec
  assert_failure 2
}

@test "can decrypt keystore when having right environment variables set" {
  # Create a keystore
  run bash -c 'yes | make org=test keygen'
  export out="$output"
  # Read the env values needed to decrypt from the generation output, and set them in the bash environment
  export ANDROID_KEYSTORE_PASSWORD_TEST=$(echo "$out" | grep ANDROID_KEYSTORE_PASSWORD_TEST | awk 'BEGIN { FS="=" } {print $2}')
  export ANDROID_KEY_PASSWORD_TEST=$(echo "$out" | grep ANDROID_KEY_PASSWORD_TEST | awk 'BEGIN { FS="=" } {print $2}')
  export ANDROID_SECRETS_IV_TEST=$(echo "$out" | grep ANDROID_SECRETS_IV_TEST | awk 'BEGIN { FS="=" } {print $2}')
  export ANDROID_SECRETS_KEY_TEST=$(echo "$out" | grep ANDROID_SECRETS_KEY_TEST | awk 'BEGIN { FS="=" } {print $2}')
  # Removed the unencrypted files
  make RM_KEY_OPTS="-f" org=test keyrm
  # Now decrypt from the encrypted version (.tar.gz.enc file)
  run make org=test keydec
  # The operation succeed and the unencrypted files are back
  assert_success
  assert [ -e './test.keystore' ]
  assert [ -e './test_private_key.pepk' ]
  assert [ -e './secrets/secrets-test.tar.gz' ]
  # Can print the content of the cert
  run make org=test keyprint
  assert_output --partial "Your keystore contains 1 entry"
  assert_output --partial "Alias name: $ANDROID_KEY_ALIAS_TEST"
  # Changing password in the env variable the keystore cannot be accessed
  export ANDROID_KEYSTORE_PASSWORD_TEST="123"
  run make org=test keyprint
  assert_failure
  assert_output --partial "IOException: keystore password was incorrect"
}
