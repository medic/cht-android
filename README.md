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
1. add icons, strings etc. in `src/<new_brand>`
1. to enable automated deployments, add the `new_brand` to `.travis.yml`

# Publishing

Create a git tag starting with `v`, e.g. `v1.2.3` and push the tag to GitHub. 

Creating this tag will trigger a Travis CI to build, sign, and properly version the build. The release-ready APKs are available for side-loading from [GitHub Releases](https://github.com/medic/medic-android/releases) and are uploaded to the Google Play Console in the "alpha" channel. To release to the public, click "Release to Production" or "Release to Beta" via the Google Play Console for each flavor.

## Copyright

Copyright 2013-2018 Medic Mobile, Inc. <hello@medicmobile.org>

## License

The software is provided under AGPL-3.0. Contributions to this project are accepted under the same license.
