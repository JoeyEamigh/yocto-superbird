BRIDGETHING_WEBAPPS_SRC_URI = "\
    https://ota.bridgething.com/webapps/stable/hub/0.5.1/hub.zip;name=hub;downloadfilename=bridgething-webapp-hub-0.5.1.zip;unpack=0 \
    https://ota.bridgething.com/webapps/stable/browser/0.5.0/browser.zip;name=browser;downloadfilename=bridgething-webapp-browser-0.5.0.zip;unpack=0 \
    https://apps.bridgething.com/r/019e6701-13f8-71b5-ba04-81f347137de2/0.1.0.zip;name=example-weather;downloadfilename=bridgething-example-weather-0.1.0.zip;unpack=0 \
    https://apps.bridgething.com/r/019e6701-13f8-71b5-ba04-85d326630e98/0.1.0.zip;name=example-calendar;downloadfilename=bridgething-example-calendar-0.1.0.zip;unpack=0 \
    https://apps.bridgething.com/r/019e7f1a-bcea-7187-a438-4e182be86a33/0.1.1.zip;name=example-home-assistant;downloadfilename=bridgething-example-home-assistant-0.1.1.zip;unpack=0 \
"

SRC_URI[hub.sha256sum] = "b411fe14c7bd5157d1762f7dbcd95027da02a7eaa26bcd552f8a6ffd147b7685"
SRC_URI[browser.sha256sum] = "d521c6775092fd33f0efd895b721cfef842e04aa43624021002d7469d6ed63c2"
SRC_URI[example-weather.sha256sum] = "b7c60ff4fbb56dc8bca2fde3d261edd99073058616d4cde8c2ceec971ed4e84d"
SRC_URI[example-calendar.sha256sum] = "52f9c9bd38e1a355a5258a2d27eb311755d3cb928d43eb3edbc42c99c9f1b319"
SRC_URI[example-home-assistant.sha256sum] = "fd95103640df5b31c198d4b19df40450efb1b4408ef51f4e44dfae1318feeae0"

BRIDGETHING_BUILTIN_ZIPS = "hub:bridgething-webapp-hub-0.5.1.zip browser:bridgething-webapp-browser-0.5.0.zip"
BRIDGETHING_EXAMPLE_ZIPS = "weather:bridgething-example-weather-0.1.0.zip calendar:bridgething-example-calendar-0.1.0.zip home-assistant:bridgething-example-home-assistant-0.1.1.zip"
