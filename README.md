Medic Mobile Android App
========================

<a href="https://travis-ci.org/medic/medic-android"><img src="https://travis-ci.org/medic/medic-android.svg"/></a>

The medic-android application is a thin wrapper to load the [CHT Core Framework](https://github.com/medic/cht-core/) web application in a webview. This allows the application to be hardcoded to a specific CHT deployment and have a partner specific logo and display name. This app also provides some deeper integration with other android apps and native phone functions that are otherwise unavailable to webapps.

# Release notes

## 0.6.0

### Upgrade notes

This release is largely intended to unblock the publishing of new apps onto the Google Play Store in a manner compatible with Android 10+. This release includes a difficult upgrade experience which may cause operational challenges in the field for apps with users on Android 10+. In particular:

1. When loading the application after upgrade you will need an internet connection in order to cache the application code.
2. The migration can take a few minutes to complete so after upgrade the user may be shown a login screen. If this happens, restart the application periodically until the user is logged in and the app loads as per usual.

We are planning improvements to [make the migration more user friendly](https://github.com/medic/medic-android/issues/134) in a future release.

Users on Android 9 and below will do not need to be migrated and will be unaffected by this change. Because of this it is recommended that projects upgrade to v0.6.0 version of their app before issuing Android 10+ devices, if possible.

Earlier releases are no longer accepted by the Google Play Store.

### Changes

- [improvement] [medic-android#106](https://github.com/medic/medic-android/issues/106): Update target SDK version to 29 for Play Store compliance

## 0.5.0

### Upgrade notes

This release changes the way in which location data is collected to better align with Play Store policies. Now the information is gathered only when filling in a form as opposed to as soon as the app is loaded.

*Note*: This breaks backwards compatibility with older versions of the CHT Core Framework which may mean that location data is no longer collected at all. Tt is recommended you upgrade to CHT v3.9.2 or later before upgrading the android app.

### Changes

- [feature] [cht-core#6380](https://github.com/medic/cht-core/issues/6380): Adds intent so opening deployment URLs will prompt to load in app
- [improvement] [medic-android#111](https://github.com/medic/medic-android/issues/111): Compliance with Play Store developer policies for PII collection disclosure
- [bug] [cht-core#6648](https://github.com/medic/cht-core/issues/6648): Blank screen when launching external apps from CHT Android app

# Development

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

## Alpha for release testing

1. Make sure all issues for this release have passed AT and been merged into `master`
2. Create a git tag starting with `v` and ending with the alpha version, e.g. `v1.2.3-alpha.1` and push the tag to GitHub.
3. Creating this tag will trigger [Travis CI](https://travis-ci.org/github/medic/medic-android) to build, sign, and properly version the build. The release-ready APKs are available for side-loading from [GitHub Releases](https://github.com/medic/medic-android/releases) and are uploaded to the Google Play Console in the "alpha" channel for only the `unbranded` and `gamma` flavors.
4. Announce the release in #quality-assurance

## Final for users

1. Create a git tag starting with `v`, e.g. `v1.2.3` and push the tag to GitHub. 
2. The exact same process as Step 3 above, but the `demo` flavour is updated also.
3. Announce the release on the [CHT forum](https://forum.communityhealthtoolkit.org), under the "Product - Releases" category.
4. Each flavor is then individually released to users via "Release Management" in the Google Play Console. Once a flavor has been tested and is ready to go live, click Release to Production

# Copyright

Copyright 2013-2020 Medic Mobile, Inc. <hello@medicmobile.org>

# License

The software is provided under AGPL-3.0. Contributions to this project are accepted under the same license.
