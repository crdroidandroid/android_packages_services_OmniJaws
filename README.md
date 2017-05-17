About
-----
OmniJaws "Just another weather service"
is a minimized service to fetch weather data from OpenWeatherMap or Yahoo.

OpenWeatherMap API
http://openweathermap.org/current

Yahoo weather API
https://developer.yahoo.com/weather/

Thanks to the original creators and contributors of the LockClock app
https://github.com/CyanogenMod/android_packages_apps_LockClock

It provided a lot of insights and knowledge how to do this
Some parts of the code like the provider access have been taken
from it with minor modificatioons. Please check the file copyright
headers for the origins of the files.

If you already include LockClock with your ROM you should
consider if you really need this. The provider API is similiar
and you dont need two different providers.

The default condition icon pack has also been extracted from
LockClock

Client access
-----
Here is a client code example using the content provider
to access weather data
https://github.com/omnirom/android_packages_services_OmniJaws/blob/android-7.1/src/org/omnirom/omnijaws/client/OmniJawsClient.java

Broadcasts and content observers
-----
There are two ways to register for changes on weather data

Using ContentObserver on URI
```java
content://org.omnirom.omnijaws.provider/weather
```

Using broadcasts
```java
private static final String ACTION_BROADCAST = "org.omnirom.omnijaws.WEATHER_UPDATE";
private static final String ACTION_ERROR = "org.omnirom.omnijaws.WEATHER_ERROR";
```

Units
-----
Depending on the value of the metric setting the following units are used to display the weather

```code
Temperature:
metric = "C"
imperial = "F"

Wind speed:
metric = "km/h"
imperial = "mph"
```

Condition icon packs support
-----
add activity with action "org.omnirom.WeatherIconPack"
the name is used to defined the prefix for the image names
default should be .weather.

```xml
		<activity
			android:name=".weather_vclouds"
			android:label="LockClock (vclouds)" >
			<intent-filter>
				<action android:name="org.omnirom.WeatherIconPack" />

				<category android:name="android.intent.category.DEFAULT" />
			</intent-filter>
		</activity>
```

An example can be found here https://github.com/maxwen/WeatherIconSample
