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

@test "can execute make key* targets" {
  run make keysetup
  assert_output --partial "'keysetup' is up to date."
}

@test "can't execute key* targets without \"org\" argument when is required" {
  run make keygen
  assert_output --partial "\"org\" name not set. Try 'make org=name keygen'.  Stop."
  refute_output --partial "Verifing the following executables"
}
