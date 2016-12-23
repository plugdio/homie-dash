# Homie Dash

A simple android app that can be used to manage and monitor Homie devices. The highlights of the app:
- it searches for homie wifi networks, connects to the selected one and uploads the config json via the config api
- it subscribes to the MQTT devices/# topic and discovers homie devices
- it provides a list of your home devices


[Homie](https://github.com/marvinroger/homie) is an MQTT convention for the IoT; created by [marvinroger](https://github.com/marvinroger) <br />
There is a great device Arduino library built for the ESP8266 at [marvinroger/homie-esp8266](https://github.com/marvinroger/homie-esp8266)