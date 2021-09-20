# Release notes

## 0.10.0

### Changes

- [feature] Add CHT External App Launcher [#199](https://github.com/medic/cht-android/pull/199)  :bookmark: cht-core v3.13.

### Development changes

- [improvement] Minor updates in the build system [#209](https://github.com/medic/cht-android/pull/209).

## 0.9.0

### Changes

- [feature] [cht-core#6741](https://github.com/medic/cht-core/issues/6741) Add more info about the version of the Android app :bookmark: cht-core v3.12.
- [improvement] [#195](https://github.com/medic/cht-android/pull/195) New branding icon, and switch from "Medic Mobile" to "Medic".
- [improvement] [#164](https://github.com/medic/cht-android/issues/164) Support Android 11.
- [feature] [#206](https://github.com/medic/cht-android/issues/206) New branded app _Alerte Niger_.
- [deprecation] Disable the _Demo_ app in the releases and in the Play Store (demo.dev.medicmobile.org is unmaintained).

### Development changes

- Upgrade Java and build dependencies. Now the base version is Java 11.
- Support new Android App Bundle (`.aab`) required by the Play Store for new apps (for now only the new app _Alerte Niger_ uses it).
- Add tooling to simplify the creation of new private keys for the Play Store.
- Improvements in the linters configuration for better code quality.
- Skip upload of APKs for `webview-armeabi-v7a` arch not used in releases.
- Improvements in the Make config and more options available for developers using the CLI instead of the Android Studio.
  Improve the development section in the README, and how to create new branded apps and private keys for signing.
- Fix instrumentations tests (UI tests) to avoid random failures, and speed up executions in Github Actions using caches.
- Fix Github Actions configuration to enable CI when the PR is created for an external contributor.

## 0.8.0

### Changes

- [improvement] [#163](https://github.com/medic/cht-android/issues/163) New connection errors UX:
  - The improvements only apply to _Webview_ flavors.
  - It also applies when the app migrates to Webview from a XWalk installation.
- [improvement] [#134](https://github.com/medic/cht-android/issues/134) New UX of Crosswalk to Webview migration:
  - Add splash screen while the data is migrated.
  - Fix bug that caused redirect to the login page after migrate.
- [improvement] Remove unused `READ_EXTERNAL_STORAGE` from the `cmmb_kenya` and `surveillance_covid19_kenya` flavors

You can see more about these changes in the release notes for [CHT Core v3.11.0](https://github.com/medic/cht-core/blob/master/release-notes/docs/3.11.0.md)

## 0.7.3

### Changes

- [improvement] [cht-android#148](https://github.com/medic/cht-android/issues/148): Remove storage access for most flavors that don't use the permission
- [improvement] New flavors added, and "livinggoods_innovation_ke" removed
- [improvement] Add new translations for the prominent disclosure for location access: Tagalog (tel), Illonggo (hil), and Bisaya (ceb), and fixed translation string for the disclosure in Nepal (ne)

### Notes

A new flavor `unbranded_test` was added with the storage permission removed for testing, while `unbranded` flavor keeps the permission from the global manifest.

## 0.7.0

### Changes

- [feature] [cht-android#136](https://github.com/medic/cht-android/issues/136): Add UI for prominent disclosure when requesting location permissions.

### Notes

The text used in the new location permission request is in the Android wrapper app itself (`cht-android`), and translated differently than [CHT Core labels](https://docs.communityhealthtoolkit.org/core/overview/translations/). Any additions or modifications to translations in `cht-android` are done in the `strings.xml` files according to the [Android localization framework](https://developer.android.com/guide/topics/resources/localization).


## 0.6.0

### Upgrade notes

This release is largely intended to unblock the publishing of new apps onto the Google Play Store in a manner compatible with Android 10+. This release includes a difficult upgrade experience which may cause operational challenges in the field for apps with users on Android 10+. In particular:

1. When loading the application after upgrade you will need an internet connection in order to cache the application code.
2. The migration can take a few minutes to complete so after upgrade the user may be shown a login screen. If this happens, restart the application periodically until the user is logged in and the app loads as per usual.

We are planning improvements to [make the migration more user friendly](https://github.com/medic/cht-android/issues/134) in a future release.

Users on Android 9 and below do not need to be migrated and will be unaffected by this change. Because of this it is recommended that projects upgrade to v0.6.0 version of their app before issuing Android 10+ devices, if possible.

Earlier releases are no longer accepted by the Google Play Store.

### Changes

- [improvement] [cht-android#106](https://github.com/medic/cht-android/issues/106): Update target SDK version to 29 for Play Store compliance.

## 0.5.0

### Upgrade notes

This release changes the way in which location data is collected to better align with Play Store policies. Now the information is gathered only when filling in a form as opposed to as soon as the app is loaded.

*Note*: This breaks backwards compatibility with older versions of the CHT Core Framework which may mean that location data is no longer collected at all. It is recommended you upgrade to CHT v3.9.2 or later before upgrading the android app.

### Changes

- [feature] [cht-core#6380](https://github.com/medic/cht-core/issues/6380): Adds intent so opening deployment URLs will prompt to load in app
- [improvement] [cht-android#111](https://github.com/medic/cht-android/issues/111): Compliance with Play Store developer policies for PII collection disclosure
- [bug] [cht-core#6648](https://github.com/medic/cht-core/issues/6648): Blank screen when launching external apps from CHT Android app
