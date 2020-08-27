Medic Mobile Android App
========================

<a href="https://travis-ci.org/medic/medic-android"><img src="https://travis-ci.org/medic/medic-android.svg"/></a>

# Installation

1. Install Android SDK
2. Clone the repo
3. Plug in your phone. Check it's detected with `adb devices`
4. Execute: `make` (will also push app unto phone)

## Connecting to the server locally
Refer to the [cht-core Developer Guide](https://github.com/medic/cht-core/blob/master/DEVELOPMENT.md#testing-locally-with-devices).

# Branding

## Building branded apps

To build and deploy APKs for all configured brands:

	make branded

## Adding new brands

To add a new brand:

1. add `productFlavors { <new_brand> { ... } }` in `build.gradle`
1. add icons, strings etc. in `src/<new_brand>`
1. to enable automated deployments, add the `new_brand` to `.travis.yml`

# Releasing

1. Create a git tag starting with `v`, e.g. `v1.2.3` and push the tag to GitHub. 
2. Creating this tag will trigger Travis CI to build, sign, and properly version the build. The release-ready APKs are available for side-loading from [GitHub Releases](https://github.com/medic/medic-android/releases) and are uploaded to the Google Play Console in the "alpha" channel for each flavor.
3. Announce the release on the [CHT forum](https://forum.communityhealthtoolkit.org), under the "Product - Releases" category.
4. Each flavor is then individually released to users via "Release Management" in the Google Play Console. Once a flavor has been tested and is ready to go live, click Release to Production

## Copyright

Copyright 2013-2018 Medic Mobile, Inc. <hello@medicmobile.org>

## License

The software is provided under AGPL-3.0. Contributions to this project are accepted under the same license.
