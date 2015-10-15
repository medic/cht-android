Medic Mobile Android App
========================

<a href="https://travis-ci.org/medic/medic-android"><img src="https://travis-ci.org/medic/medic-android.svg"/></a>

# Installation

1. Install Android SDK
2. Clone the repo
3. Plug in your phone
4. Execute: `make`

# Branding

## Building branded apps

To build and deploy APKs for all configured brands:

	make branded

## Adding new brands

To add a new brand:

1. add `productFlavors { <new_brand> { ... } }` in `build.gradle`
2. add icons, strings etc. in `src/<new_brand>`

# Publishing

To publish to the play store:

1. identify the git tag of the app version you want to build
1. log in to Jenkins
1. trigger job `medic-android`, supplying the version number/git tag from step **1**
1. wait for `medic-android` job to complete successfully
1. upload the APK to the play store

	_initial load_: upload the .apk file to the APK page in the play store dev console

	_updates_: trigger the job `medic-android-publish`, selecting the version and branding you wish to publish

1. repeat the previous step if you want to publish the app for multiple brands
