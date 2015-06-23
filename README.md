Medic Mobile Demo Webapp Mobile Demo
====================================

# Installation

1. Plug in your android phone
2. execute:

	git clone git@github.com:alxndrsn/medic-webapp-mobile-demo.git && \
	cd medic-webapp-mobile-demo && \
	make

This will build the app with embedded credentials for the medic mobile demo at https://demo.app.medicmobile.org/.  To disable this feature, set the `DEFAULT_TO_DEMO_APP` env var to `false`, e.g.:

	DEFAULT_TO_DEMO_APP=false make
