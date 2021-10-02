CHT Android App
===============

The cht-android application is a thin wrapper to load the [CHT Core Framework](https://github.com/medic/cht-core/) web application in a webview. This allows the application to be hardcoded to a specific CHT deployment and have a partner specific logo and display name. This app also provides some deeper integration with other android apps and native phone functions that are otherwise unavailable to webapps.

# Android App Bundles

The build script produces multiple AABs for publishing to the **Google Play Store**, so the generated `.aab` files need to be uploaded instead of the `.apk` files if the Play Store require so. Old apps published for the first time before Aug 1,  2021 can be updated with the APK format.

For each flavor two bundles are generated, one for each rendering engine: _Webview_ and _Xwalk_. When distributing via the Play Store using the bundle files, upload all AABs and it will automatically choose the right one for the target device.

The AABs are named as follows: `cht-android-{version}-{brand}-{rendering-engine}-release.aab`.

| Rendering engine | Android version |
|------------------|-----------------|
| `webview`        | 10+             |
| `xwalk`          | 4.4 - 9         |

# APKs

For compatibility with a wide range of devices, the build script produces multiple APKs. The two variables are the instruction set used by the device's CPU, and the supported Android version. When sideloading the application, it is essential to pick the correct APK or the application may crash.

If distributing APKs via the Play Store, upload all APKs and it will automatically choose the right one for the target device.

To help you pick which APK to install, you can find information about the version of Android and the CPU in the About section of the phone's settings menu.

The APKs are named as follows: `cht-android-{version}-{brand}-{rendering-engine}-{instruction-set}-release.apk`.

| Rendering engine | Instruction set | Android version | Notes                                                       |
|------------------|-----------------|-----------------|-------------------------------------------------------------|
| `webview`        | `arm64-v8a`     | 10+             | Preferred. Use this APK if possible.                        |
| `webview`        | `armeabi-v7a`   | 10+             | Built but not compatible with any devices. Ignore this APK. |
| `xwalk`          | `arm64-v8a`     | 4.4 - 9         |                                                             |
| `xwalk`          | `armeabi-v7a`   | 4.4 - 9         |                                                             |


# Release notes

Checkout the release notes in the [Changelog](CHANGELOG.md) page, our you can see the full release history with the installable files for sideloading [here](https://github.com/medic/cht-android/releases).


# Development

1. Install the [Android SDK](https://developer.android.com/studio#command-tools) and Java 11+ (it works with OpenJDK versions).
2. Clone the repository.
3. Plug in your phone. Check it's detected with `adb devices`.
4. Execute: `make` (will also push app into the phone). Use `make xwalk` for the Xwalk version instead.

Gradle is also used but it's downloaded and installed in the user space the first time `make` is executed.

You can also build and launch the app with [Android Studio](#android-studio).

## Flavor selection

Some `make` targets support the flavor and the rendering engine as `make flavor=[Flavor][Engine] [task]`, where `[Flavor]` is the branded version with the first letter capitalized and the `[Engine]` is either `Webview` or `Xwalk`. The `[task]` is the action to execute: `deploy`, `assemble`, `lint`, etc.

The default value for `flavor` is `UnbrandedWebview`, e.g. executing `make deploy` will assemble and install that flavor, while executing `make flavor=MedicmobilegammaXwalk deploy` will do the same for the _Medicmobilegamma_ brand and the `Xwalk` engine.

See the [Makefile](./Makefile) for more details.

## Build and assemble

    $ make assemble

The command above builds and assembles the _debug_ and _release_ APKs of the Unbranded Webview version of the app.

Each APK will be generated and stored in `build/outputs/apk/[flavor][Engine]/[debug|release]/`, for example after assembling the _Simprints Webview_ flavor with `make flavor=SimprintsWebview assemble`, the _release_ versions of the APKs generated are stored in `build/outputs/apk/simprintsWebview/release/`.

To assemble other flavors, use the following command: `make flavour=[Flavor][Engine] assemble`. See the [Flavor selection](#flavor-selection) section for more details about `make` commands.

To create the `.aab` bundle file, use `make bundle`, although signed versions are generated when [releasing](#releasing), and the Play Store requires the AAB to be signed with the right key.

To clean the APKs and compiled resources: `make clean`.

## Static checks

To only execute the **linter checks**, run: `make lint`. To perform the same checks for the _XView_ source code, use: `make flavor=UnbrandedXwalk lint` instead.

## Testing

To execute unit tests: `make test` (static checks ara also executed).

### Instrumentation Tests (UI Tests)

These tests run on your device.

1. Uninstall previous versions of the app, otherwise an `InstallException: INSTALL_FAILED_VERSION_DOWNGRADE` can make the tests fail.
2. Select English as default language in the app.
3. Execute steps 1 to 3 from [Development](#development).
4. Execute: `make test-ui` or `make test-ui-gamma`.

### Connecting to the server locally

Refer to the [CHT Core Developer Guide](https://github.com/medic/cht-core/blob/master/DEVELOPMENT.md#testing-locally-with-devices).

## Android Studio

The [Android Studio](https://developer.android.com/studio) can be used to build and launch the app instead. Be sure to select the right flavor and rendering engine from the _Build Variants_ dialog (see [Build and run your app](https://developer.android.com/studio/run)). To launch the app in an emulator, you need to uncomment the code that has the strings for the `x86` or the `x86_64` architecture in the `android` / `splits` / `include` sections of the `build.gradle` file.


# Branding

Starting Aug 2021 Google [changed](https://developer.android.com/guide/app-bundle) the way new apps are published in the Play Store, so older apps in this repo only need to upload the release APK files to update the app, while apps created after Aug 2021 do so uploading the AAB files. Moreover, the creation of the App requires not just the basic configurations like the AAB files, name of the app, description and so on, but also requires to register the key that we use to sign the AAB files, so Google can create optimized APK versions for the users signed with the same key the .aab files were created.

In summary the steps for a new app are:

1. Setup the new flavor in the source code and in the CI pipeline to build and sign the app when releasing (next section).
2. Choose a keystore under the [secrets/](secrets/) folder to sign the new app (next section), the old keystore used by medic (`secrets-medic.tar.gz.enc`) cannot be used to create new apps, is only used to keep signing the old apps. Each partner usually publish one app and should has its own keystore, although in case it has more than one app the same keystore can be used. If the partner doesn't have its own keystore, follow the instructions in the next section.
3. With the first version created after tagging and releasing in Github, go to the Play Store Console and create the app, uploading the first .aab files along with the key file.

### New brand

These are the steps to create a new branded App. Each branded app has an identifier that is used to identify and configure it in different parts of the source code and when invoking some commands. In the instructions below we will use as example the id `new_brand`.

1. Add `productFlavors { <new_brand> { ... } }` in `build.gradle`, e.g.:

   ```groovy
       new_brand {
         dimension = 'brand'
         applicationId = 'org.medicmobile.webapp.mobile.new_brand'
       }
   ```

2. Add icons, strings etc. in the `src/<new_brand>` folder. It's required to place there at least the `src/new_brand/res/values/strings.xml` file with the name of the app and the URL of the CHT instance:

   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <resources>
       <string name="app_name">New Brand</string>
       <string name="app_host">new_brand.app.medicmobile.org</string>
   </resources>
   ```

3. Enable automated builds of the APKs and AABs: add the `new_brand` flavor in `.github/workflows/publish.yml`. The _Unpack secrets  ..._ task unpacks and decrypts the secret file with the keystore, The _Assemble ..._ task takes care of generating the `.apk` files for sideloading, and the _Bundle ..._ task is responsible of generating the `.aab` files for publishing in the Play Store:

   ```yml
       - name: Unpack secrets new_brand
         env:
           ANDROID_SECRETS_KEY: ${{ secrets.ANDROID_SECRETS_KEY_NEW_BRAND }}
           ANDROID_SECRETS_IV: ${{ secrets.ANDROID_SECRETS_IV_NEW_BRAND }}
         run: make org=new_brand keydec

       - name: Assemble new_brand
         uses: maierj/fastlane-action@v1.4.0
         with:
           lane: build
           options: '{ "flavor": "new_brand" }'
         env:
           ANDROID_KEYSTORE_PATH: new_brand.keystore
           ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD_NEW_BRAND }}
           ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD_NEW_BRAND }}
   
       - name: Bundle new_brand
         uses: maierj/fastlane-action@v1.4.0
         with:
           lane: bundle
           options: '{ "flavor": "new_brand" }'
         env:
           ANDROID_KEYSTORE_PATH: new_brand.keystore
           ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD_NEW_BRAND }}
           ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD_NEW_BRAND }}
   ```

   The variables in the `env` sections point to a keystore and the passwords to unlock the keystore that will be generated in the following steps, but it's important to follow the name convention, in the example all the variables that are configured in Github CI end with the suffix `_NEW_BRAND`, these variables need to be added in the cht-android repo settings by a manager of Medic.

4. Generate the keystore: the keystore files are placed into a compressed and encrypted file in the [secrets/](secrets/) folder. In our case the file will be `secrets/secrets-new_brand.tar.gz.enc`, and the content inside when the file is decrypted is:

   - `new_brand.keystore`: the Java keystore with a signature key inside that is always called `medicmobile`. It's used to sign the APKs and the bundles, and the one that Google will use to sign the optimized APKs that generates in the Play Store.
   - `new_brand_private_key.pepk`: a PEPK file is an encrypted file that contains inside the `medicmobile` key from the keystore above, ready to be uploaded to the Play Store the first time the app is registered in the Play Console. The file is only used there, but kept in the compressed file as a backup.

  Don't worry to follow all the name conventions and how to generate these files, you can create a new keystore, the passwords and the PEPK file with only one command: `make org=new_brand keygen`. Executing the command will check that you have the necessary tooling installed, and ask you the information about the certificate like the organization name, organization unit, etc. The command also takes care of picking random passwords that meet the security requirements, and then compresses the key files and finally encrypt the `.tar.gz` file into the `.enc` file. At the end of the execution, the script will also show the list of environment variables that you have to setup in CI and locally in order to sign apps with the new keystore.

   ```
   $ make org=new_brand keygen
   Verifing the following executables are in the $PATH: java keytool openssl ...
   keytool -genkey -storepass dd8668... -v -keystore new_brand.keystore -alias medicmobile -keyalg RSA -keysize 2048 -validity 9125
   What is your first and last name?
     [Unknown]:  
   What is the name of your organizational unit?
     [Unknown]:  New Brand
   What is the name of your organization?
     [Unknown]:  Medic Mobile
   What is the name of your City or Locality?
     [Unknown]:  San Fran... ...
   Is CN=Unknown, OU=New Brand, O=Medic Mobile, L=San Francisco, ST=CA, C=US correct?
     [no]:  y
   
   Generating 2,048 bit RSA key pair and self-signed certificate (SHA256withRSA) with a validity of 9,125 days
       for: CN=Unknown, OU=New Brand, O=Medic Mobile, L=San Francisco, ST=CA, C=US
   [Storing new_brand.keystore]
   ... ...
   
   #######################################      Secrets!    #######################################
   #                                                                                              #
   # The following environment variables needs to be added to the CI environment                  #
   # (Github Actions), and to your local environment if you also want                             #
   # to sign APK or AAB files locally:                                                            #
   #                                                                                              #
   
   export ANDROID_KEYSTORE_PASSWORD_NEW_BRAND=dd8668...
   export ANDROID_KEY_PASSWORD_NEW_BRAND=dd8668...
   export ANDROID_SECRETS_IV_NEW_BRAND=88d9c2dea7a9...
   export ANDROID_SECRETS_KEY_NEW_BRAND=2824d02d2bc221f5844b8fe1d928211dcbbc...
   export ANDROID_KEYSTORE_PATH_NEW_BRAND=new_brand.keystore
   export ANDROID_KEY_ALIAS_NEW_BRAND=medicmobile
   
   #
   # The file secrets/secrets-new_brand.tar.gz.enc was created and has to be added to the git
   # repository (don't worry, it's encrypted with some of the keys above).                        #
   # NOTE: *keep the environment variables secret !!*                                             #
   #                                                                                              #
   ###########################################  End of Secrets  ###################################
   ```

   The _Secrets!_ section at the end is as important as the `secrets/secrets-new_brand.tar.gz.enc` file generated, because as it says above, it needs to be configured in CI.

   Use a safe channel to send to the manager in charge, like a password manager, and keep them locally at least for testing, storing in a script file that is safe in your computer.

   If you want to start over because some of the parameters were wrong, just execute `make org=new_brand keyrm-all` to clean all the files generated. Once committed the `.enc` file, you can delete the uncompressed and unencrypted version with `make org=new_brand keyrm`, it will delete the `new_brand.keystore`, `new_brand_private_key.pepk`, and the unencrypted `.tar.gz` files, that are safer kept in the `.tar.gz.enc` file.

   To decrypt the content like CI does to sign the app, execute: `make org=new_brand keydec`, it will decrypt and decompress the files removed in the step above. Remember that the environment variables printed in the console needs to be loaded in the CLI. Note that all the variables above end with the suffix `_NEW_BRAND`, as the id of the app that we pass through the `org` argument in lowercase, but if Make found the same variables defined without the prefix, they take precedence over the suffix ones.

#### Use the keystore

**Want to check the keystore?** here are a few things you must test before upload to the repo:

1. Execute `make org=new_brand keyprint` to see the certificate content, like the org name, the certificate fingerprints, etc.

2. Sign your app! try locally to build the app with the certificate. To create the Webview versions of the .apk files: `make org=new_brand flavor=New_brandWebview assemble`. the "release" files signed should be placed in `build/outputs/apk/new_brandWebview/release/`. To ensure the files were signed with the right signature execute `make keyprint-apk`, it will check the certificate of the first apk file under the `build/` folder:

```
$ make keyprint-apk 
apksigner verify -v --print-certs build/outputs/apk/new_brandWebview/release/cht-android-SNAPSHOT-new_brand-webview-armeabi-v7a-release.apk
... ...
Verified using v2 scheme (APK Signature Scheme v2): true
... ...
Signer #1 certificate DN: CN=Unknown, OU=New Brand, O=Medic Mobile, L=San Francisco, ST=CA, C=US
Signer #1 certificate SHA-256 digest: 7f072b...
```

Also do the same for the bundle format: build and verify, despite the AAB are not useful for local development. In our example, execute first `make org=new_brand flavor=New_brandWebview bundle`, and then `make keyprint-bundle` to see the signature of one of the `.aab` files generated.

Because the files generated here are signed with the same key that you are going to use in CI, and the files produced in CI will be uploaded to the Play Store later, any file generated locally following the steps above will be compatible with any installation made from the Play Store, means that if a user install the app from the Play Store, and then we want to replace the installation with an alpha version generated in CI or a local version generated in dev environment, it will work without requiring the user to uninstall the app and lost the data.


# Releasing

These are the steps to follow when creating a new release in the Play Store:

## Alpha for release testing

1. Make sure all issues for this release have passed AT and been merged into `master`.
2. Create a git tag starting with `v` and ending with the alpha version, e.g. `v1.2.3-alpha.1` and push the tag to GitHub.
3. Creating this tag will trigger [GitHub Action](https://github.com/medic/cht-android/actions) to build, sign, and properly version the build. The release-ready APKs are available for side-loading from [GitHub Releases](https://github.com/medic/cht-android/releases), along with the AABs that may be required by the Google Play Store.
4. Announce the release in #quality-assurance.


### New App in the Play Store

Remember that when the app is created in the Play Store, it's required to choose the way the app will be signed by Google: we upload the signed AAB files, but then Google creates optimized versions of the app in .apk format. The app has to be configured to use the same signing and upload signatures by Google. Choose to upload a "Java keystore", the Play Console will require a file encrypted with a tool named PEPK, that file is the `<brand>_private_key.pepk` file generated when following the instructions of [New brand](#new-brand) (the button to upload the `.pepk` in the Play Console may say "Upload generated ZIP" although the PEPK file doesn't look like a .zip file). Read the section to know how to extract that file from the encrypted file stored in the `secrets/` folder if you don't have it when publishing for the first time. Once upload the first time, you don't need it anymore in order to publish new versions of the app.

## Final for users

1. Create a git tag starting with `v`, e.g. `v1.2.3` and push the tag to GitHub. 
2. The exact same process as Step 3 above.
3. Publish the unbranded, simprints, and gamma flavors to the Play Store.
4. Announce the release on the [CHT forum](https://forum.communityhealthtoolkit.org), under the "Product - Releases" category.
5. Each flavor is then individually released to users via "Release Management" in the Google Play Console. Once a flavor has been tested and is ready to go live, click Release to Production.


# Copyright

Copyright 2013-2021 Medic Mobile, Inc. <hello@medic.org>.


# License

The software is provided under AGPL-3.0. Contributions to this project are accepted under the same license.
