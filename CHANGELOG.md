# Compatibility

| CHT Android | Android OS | cht-core |
| -- | -- | -- |
| 1.x | 5.0+ | 3.x and 4.x |
| 0.x | 4.4+ | 3.x |

# Release notes

## 1.0.1

This release fixes two bugs:
1. Users unable to install two different apps or two different flavors [#287](https://github.com/medic/cht-android/issues/287).
1. A fix for an implicit internal intent vulnerability [#282](https://github.com/medic/cht-android/issues/282).

## 1.0.0

This release switches from Crosswalk to WebView ([#169](https://github.com/medic/cht-android/issues/169)) which brings great benefits, such as:
- Newer version of Chrome that has better performance, better security, more features and fewer bugs.
- Drop Crosswalk library that has been unsupported since 2016.
- Support for upcoming versions of CHT Core.
- Smaller APK size. Before it was 27.3MB, now it is 381KB.

### Upgrade steps

First, [build and publish](https://docs.communityhealthtoolkit.org/core/guides/android/branding/) your cht-android brand using the `v1.0.0` tag.

Then for each phone:
1. Upgrade [WebView](https://play.google.com/store/apps/details?id=com.google.android.webview) to the latest available version in Google Play Store and enable automatic updates of this app by following the [Google Documentation](https://support.google.com/googleplay/answer/113412). This will ensure top performance when using CHT.
2. Install your brand of cht-android 1.0.0.

### Performance improvements

This is the performance test result that compares CHT Android v0.8.0 with the new CHT Android v1.0.0.
- **Device:** Sony Xperia Z1
- **Android:** 5.1.1
- **CHT Core:** 3.15.0

| Test | v0.8.0 | v1.0.0 | Improvement |
| ------------ | ------------ | ------------ | ------------ |
| Initial load (fetching 1882 documents) | 0:03:14  | 0:01:35  | **104%** |
| Refreshing after initial replication | 0:00:10  | 0:00:03  | **233%** |
| Loading Contacts tab | 0:00:17  |  0:00:12 | **42%** |
| Loading Reports tab | 0:00:23  | 0:00:21  | **9%** |
| Loading a specific report (New Pregnancy) | 0:00:04  |  0:00:02 | **100%** |
| Track opening a form (New Pregnancy) | 0:00:05  | 0:00:04  | **25%** |
| Loading Tasks tab | 0:00:02  | 0:00:01  | **100%** |

### Breaking changes

- [breaking] Drop support for Android 4.4 [#169](https://github.com/medic/cht-android/issues/169). Any devices still running Android 4.4 must be upgraded or replaced with newer devices prior to install. If in doubt, reach out on the [forum](https://forum.communityhealthtoolkit.org/) and we can guide you through the process.
- [breaking] Remove Simprints integration [#230](https://github.com/medic/cht-android/issues/230). This feature had already regressed so it's unlikely you're using it.

### Improvements

- [improvement] Check and handle when location permission is denied or granted [#189](https://github.com/medic/cht-android/issues/189). CHT Android is now aligned with best practices when requesting location permissions in Android.
- [improvement] Request "Approximate" and "Precise" location since they are required in Android 12 [#207](https://github.com/medic/cht-android/issues/207). Users with Android 12 or higher can grant approximate or precise location when using the app.
- [improvement] Replace deprecated ImagePicker [#159](https://github.com/medic/cht-android/issues/159). CHT Android is now using a native way to upload images in the forms.
- [performance] Android app should navigate to root instead of the _rewrite path after webapp v3.5.x [#94](https://github.com/medic/cht-android/issues/94). The navigation after setting up the app has been simplified improving the initial load performance.
- [feature] Make it clear when a training app is in use [#258](https://github.com/medic/cht-android/issues/258). Add a distinctive border and message when using the training app by following the instructions in our [documentation](https://docs.communityhealthtoolkit.org/apps/guides/onboarding/#setting-up-a-training-app).
- [bug] Border not showing red for production URL in dev app [#266](https://github.com/medic/cht-android/issues/266). A distinctive red border is displayed when using the Medic (unbranded) flavor app in Medic hosted deployments.
- [bug] Prominent disclosure when "handling users' Files" [#148](https://github.com/medic/cht-android/issues/148). CHT Android is now aligned with best practices when requesting storage permissions in Android, users can now approve the storage permission request. 
- [deprecation] Remove unbranded-test flavor [#257](https://github.com/medic/cht-android/issues/257). This test flavor was not in used anymore.
- [deprecation] Remove the old menu and lock pin screen [#253](https://github.com/medic/cht-android/issues/253). These features weren't used anymore, the old menu was launched when pressing a hardware menu button that old phones used to have, manufactures removed this button 8+ years ago.

### Development changes

- [improvement] Upgrade to Android 12 [#205](https://github.com/medic/cht-android/issues/205). CHT Android is now up to date with the latest Android version.
- [improvement] Migrate off of the unmaintained com.noveogroup.android:check plugin [#242](https://github.com/medic/cht-android/issues/242). The deprecated static check library was replaced with latest versions of Checkstyle, PMD and SpotBugs which ensures code quality.
- [improvement] PMD Processing errors for files that are not .java [#255](https://github.com/medic/cht-android/issues/255). This improve the performance when running static checks in CHT Android.

## 0.11.0

### Changes

- [feature] Remember previous URL when reloading app [#52](https://github.com/medic/cht-android/issues/52).
- [improvement] Allow users to set cht-core URLs with leading or trailing spaces (and trailing slash) on unbranded app [#178](https://github.com/medic/cht-android/issues/178).
- [improvement] Update labels to use generic app name [#128](https://github.com/medic/cht-android/issues/128).


### Development changes

- [improvement] Deduplicate and improve development docs [#214](https://github.com/medic/cht-android/issues/214).
  - [New `Android` section](https://docs.communityhealthtoolkit.org/core/guides/android/) added to the CHT Documentation site with the cht-android documentation that was previously split between the README and various other sections of the CHT Documentation.
- [improvement] Fix Makefile targets for keystore management [#222](https://github.com/medic/cht-android/issues/222).
- [improvement] Upgrade Gradle, plugins and test dependencies [#232](https://github.com/medic/cht-android/pull/232).

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
