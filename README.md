Medic Mobile Android App
========================

# Build

Travis build status: <a href="https://travis-ci.org/medic/medic-android"><img src="https://travis-ci.org/medic/medic-android.svg"/></a>

# Installation

1. Plug in your android phone
2. Clone the repo
3. execute:

	make

# Branding

To add a new brand:

1. add `productFlavors { <new_brand> { ... } }` in `build.gradle`
2. add icons, strings etc. in `src/<new_brand>`
