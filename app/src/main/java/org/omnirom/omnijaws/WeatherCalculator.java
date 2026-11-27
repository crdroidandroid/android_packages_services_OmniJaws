/*
 * Copyright (C) 2026 crDroid Android project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnirom.omnijaws;

/** Derives values a provider left out from the ones it reported. */
public class WeatherCalculator {
    // Magnus formula coefficients
    private static final double MAGNUS_A = 17.625;
    private static final double MAGNUS_B = 243.04;

    public static void fillMissing(WeatherInfo w) {
        boolean metric = w.isMetric();
        double temp = toCelsius(w.getTemperature(), metric);
        if (Double.isNaN(temp)) {
            return;
        }

        double humidity = w.getHumidity();
        double dewPoint = toCelsius(w.getDewPoint(), metric);
        if (Double.isNaN(humidity) && !Double.isNaN(dewPoint)) {
            humidity = Math.min(100.0, relativeHumidity(temp, dewPoint));
            w.setHumidity((float) humidity);
        } else if (Double.isNaN(dewPoint) && humidity > 0) {
            w.setDewPoint(fromCelsius(dewPoint(temp, humidity), metric));
        }

        double wind = w.getWindSpeed();
        if (Float.isNaN(w.getFeelsLike()) && !Double.isNaN(humidity) && !Double.isNaN(wind)) {
            double windMs = metric ? wind / 3.6 : wind * 0.44704;
            w.setFeelsLike(fromCelsius(apparentTemperature(temp, humidity, windMs), metric));
        }
    }

    static double dewPoint(double tempC, double humidity) {
        double gamma = Math.log(humidity / 100.0) + MAGNUS_A * tempC / (MAGNUS_B + tempC);
        return MAGNUS_B * gamma / (MAGNUS_A - gamma);
    }

    static double relativeHumidity(double tempC, double dewPointC) {
        return 100.0 * Math.exp(MAGNUS_A * dewPointC / (MAGNUS_B + dewPointC)
                - MAGNUS_A * tempC / (MAGNUS_B + tempC));
    }

    // Australian Bureau of Meteorology apparent temperature, shade version
    static double apparentTemperature(double tempC, double humidity, double windMs) {
        double vapourPressure = humidity / 100.0 * 6.105
                * Math.exp(17.27 * tempC / (237.7 + tempC));
        return tempC + 0.33 * vapourPressure - 0.70 * windMs - 4.00;
    }

    private static double toCelsius(float value, boolean metric) {
        return metric ? value : (value - 32.0) / 1.8;
    }

    private static float fromCelsius(double value, boolean metric) {
        return (float) (metric ? value : value * 1.8 + 32.0);
    }
}
