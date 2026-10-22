BRIDGETHING_WEBAPPS_SRC_URI = "\
    https://ota.bridgething.com/webapps/stable/hub/0.14.0/hub.zip;name=hub;downloadfilename=bridgething-webapp-hub-0.14.0.zip;unpack=0 \
    https://ota.bridgething.com/webapps/stable/browser/0.14.0/browser.zip;name=browser;downloadfilename=bridgething-webapp-browser-0.14.0.zip;unpack=0 \
    https://apps.bridgething.com/r/019e6701-13f8-71b5-ba04-81f347137de2/0.9.3.zip;name=example-weather;downloadfilename=bridgething-example-weather-0.9.3.zip;unpack=0 \
    https://apps.bridgething.com/r/019e6701-13f8-71b5-ba04-85d326630e98/0.9.0.zip;name=example-calendar;downloadfilename=bridgething-example-calendar-0.9.0.zip;unpack=0 \
    https://apps.bridgething.com/r/019e7f1a-bcea-7187-a438-4e182be86a33/0.9.1.zip;name=example-home-assistant;downloadfilename=bridgething-example-home-assistant-0.9.1.zip;unpack=0 \
"

SRC_URI[hub.sha256sum] = "431276d4cfbfa6d7dd6d2bfd816a6c7f121b8c13294a1626a18135e0915ab91d"
SRC_URI[browser.sha256sum] = "ed1c64466ab3dd22288be89e092b49e7de6e1475c15a035061b38b156417e8c8"
SRC_URI[example-weather.sha256sum] = "f679d4d6721e7a226ec720074dff589ea9ab0ed9f9dc20ae150497f9216811eb"
SRC_URI[example-calendar.sha256sum] = "cfee3b8a2d299831a3088a8c35dc92912a5a467bc84180569599b52e1b58c5ab"
SRC_URI[example-home-assistant.sha256sum] = "5871c796bfb7c321491786feafb828fa173f6224267bbb2ce1314c75aec25b82"

BRIDGETHING_BUILTIN_ZIPS = "hub:bridgething-webapp-hub-0.14.0.zip browser:bridgething-webapp-browser-0.14.0.zip"
BRIDGETHING_EXAMPLE_ZIPS = "weather:bridgething-example-weather-0.9.3.zip calendar:bridgething-example-calendar-0.9.0.zip home-assistant:bridgething-example-home-assistant-0.9.1.zip"
