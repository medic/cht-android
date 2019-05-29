Medic Mobile Android App
========================

<a href="https://travis-ci.org/medic/medic-android"><img src="https://travis-ci.org/medic/medic-android.svg"/></a>

# Installation

1. Install Android SDK
2. Clone the repo
3. Plug in your phone. Check it's detected with `adb devices`
4. Execute: `make` (will also push app unto phone)

## Connecting to the server locally
Refer to https://github.com/medic/medic#testing-locally-with-devices.

# Branding

## Building branded apps

To build and deploy APKs for all configured brands:

	make branded

## Adding new brands

To add a new brand:

1. add `productFlavors { <new_brand> { ... } }` in `build.gradle`
2. add icons, strings etc. in `src/<new_brand>`


# Releasing

To release a new version, create a git tag starting with `v`, e.g. `v1.2.3`, and push this to GitHub.  Travis CI will build the release and push the debug APK to https://github.com/medic/medic-android/releases.


# Publishing

To publish to the play store:

1. identify the git tag of the app version you want to build
1. open remote desktop connection to our Jenkins server, windows.dev.medicmobile.org
1. log in to Jenkins on remote desktop (http://localhost:8080)
1. trigger job `medic-android`, supplying the version number/git tag from step **1** as the "VERSION_TO_BUILD" (eg 0.1.93)
1. wait for `medic-android` job to complete successfully
1. upload the APK to the play store

	_initial load_: upload the .apk file to the APK page in the play store dev console

	_updates_: trigger the job `medic-android-publish`, selecting the version, release track, and branding you wish to publish

1. repeat the previous step if you want to publish the app for multiple brands

## Copyright

Copyright 2013-2018 Medic Mobile, Inc. <hello@medicmobile.org>

## License

The software is provided under AGPL-3.0. Contributions to this project are accepted under the same license.
