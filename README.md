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
and you dont need to different providers.

The default condition icon pack have also been extracted from 
LockClock

Condition icon packs support
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
