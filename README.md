Medic Mobile Android App
========================

# Build

<a href="https://travis-ci.org/medic/medic-android"><img src="https://travis-ci.org/medic/medic-android.svg"/></a>

# Installation

1. Install Android SDK
2. Clone the repo
3. Execute: `make`
4. Plug in your phone
5. Install the desired SDK: `adb install build/outputs/apk/<apk name>`

# Branding

To add a new brand:

1. add `productFlavors { <new_brand> { ... } }` in `build.gradle`
2. add icons, strings etc. in `src/<new_brand>`
