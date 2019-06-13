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

To build and deploy builds for all configured brands:

	make branded

## Adding new brands

To add a new brand:

1. add `productFlavors { <new_brand> { ... } }` in `build.gradle`
2. add icons, strings etc. in `src/<new_brand>`

# Releasing

To release a new version, create a git tag starting with `v`, e.g. `v1.2.3`, and push this to GitHub. Travis CI will build the release and push a signed, versioned, release-ready bundle to https://github.com/medic/medic-android/releases.

# Publishing

To publish to [GitHub](https://github.com/medic/medic-android/releases):

For each brand you'd like to publish:

1. Download the bundle you'd like to release from [GitHub Releases](https://github.com/medic/medic-android/releases).
1. Navigate to the Google Play Console. `Release management` > `App releases`.
1. Upload the bundle

## Copyright

Copyright 2013-2018 Medic Mobile, Inc. <hello@medicmobile.org>

## License

The software is provided under AGPL-3.0. Contributions to this project are accepted under the same license.
