BRIDGETHING_WEBAPPS_SRC_URI = "\
    https://ota.bridgething.com/webapps/stable/hub/0.9.0/hub.zip;name=hub;downloadfilename=bridgething-webapp-hub-0.9.0.zip;unpack=0 \
    https://ota.bridgething.com/webapps/stable/browser/0.9.0/browser.zip;name=browser;downloadfilename=bridgething-webapp-browser-0.9.0.zip;unpack=0 \
    https://apps.bridgething.com/r/019e6701-13f8-71b5-ba04-81f347137de2/0.9.0.zip;name=example-weather;downloadfilename=bridgething-example-weather-0.9.0.zip;unpack=0 \
    https://apps.bridgething.com/r/019e6701-13f8-71b5-ba04-85d326630e98/0.9.0.zip;name=example-calendar;downloadfilename=bridgething-example-calendar-0.9.0.zip;unpack=0 \
    https://apps.bridgething.com/r/019e7f1a-bcea-7187-a438-4e182be86a33/0.9.0.zip;name=example-home-assistant;downloadfilename=bridgething-example-home-assistant-0.9.0.zip;unpack=0 \
"

SRC_URI[hub.sha256sum] = "0a488f8651ef5e7e14148cb81e209ad8bef1c31a50c1e2e9031df4ceb181701b"
SRC_URI[browser.sha256sum] = "1f678d3ca2f311ceb6a2a501ac70e5d6404efa325b5578e6b23880564d80488f"
SRC_URI[example-weather.sha256sum] = "2128845959bfc84913c69e9ba46f68e24b57dbed6a243ee1dad2c0311e877a72"
SRC_URI[example-calendar.sha256sum] = "cfee3b8a2d299831a3088a8c35dc92912a5a467bc84180569599b52e1b58c5ab"
SRC_URI[example-home-assistant.sha256sum] = "59a81ace30cda1a9a6c026f02168b6638dc942f1987e1fa7364f369916a0a3f4"

BRIDGETHING_BUILTIN_ZIPS = "hub:bridgething-webapp-hub-0.9.0.zip browser:bridgething-webapp-browser-0.9.0.zip"
BRIDGETHING_EXAMPLE_ZIPS = "weather:bridgething-example-weather-0.9.0.zip calendar:bridgething-example-calendar-0.9.0.zip home-assistant:bridgething-example-home-assistant-0.9.0.zip"
