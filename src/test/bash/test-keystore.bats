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
  assert_output --partial "'keysetup' is up to date."
}

@test "can't execute make key* targets when one of the tool is missed" {
  run make XXD=notxxd keysetup
  refute_output --partial "'keysetup' is up to date."
  assert_output --partial "\"No command 'notxxd' in \$PATH\".  Stop."
}

@test "can't execute key* targets without \"org\" argument when is required" {
  run make keygen
  assert_output --partial "\"org\" name not set. Try 'make org=name keygen'.  Stop."
  refute_output --partial "Verifying the following executables"
}

@test "can generate and encrypt keystore" {
  run bash -c 'yes | make org=test keygen'
  refute_output --partial "\"org\" name not set. Try 'make org=name keygen'.  Stop."
  assert_output --partial "Verifying the following executables"
  assert_output --partial "keytool -genkey -storepass"
  assert_output --partial "Secrets!"
  assert [ -e './test.keystore' ]
  assert [ -e './test_private_key.pepk' ]
  assert [ -e './secrets/secrets-test.tar.gz' ]
  assert [ -e './secrets/secrets-test.tar.gz.enc' ]
  ANDROID_KEYSTORE_PASSWORD_TEST=$(echo $output | grep -oP "ANDROID_KEYSTORE_PASSWORD_TEST=\K\w+")
  assert [ ! -z "$ANDROID_KEYSTORE_PASSWORD_TEST" ]
}

@test "can remove all the generated files except the encrypted file" {
  run bash -c 'yes | make org=test keygen'
  make RM_KEY_OPTS="-f" org=test keyrm
  assert [ ! -e './test.keystore' ]
  assert [ ! -e './test_private_key.pepk' ]
  assert [ ! -e './secrets/secrets-test.tar.gz' ]
  assert [ -e './secrets/secrets-test.tar.gz.enc' ]
}
