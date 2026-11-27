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

/** Sunrise equation, for providers that report no sun times. */
public class SunCalculator {
    private static final double J2000 = 2451545.0;
    private static final double JD_UNIX_EPOCH = 2440587.5;
    private static final double MS_PER_DAY = 86400000.0;

    /**
     * Sunrise and sunset in ms for the solar day around {@code timeMs},
     * or null during polar day or night.
     */
    public static long[] getSunriseSunset(double latitude, double longitude, long timeMs) {
        double jd = timeMs / MS_PER_DAY + JD_UNIX_EPOCH;
        double n = Math.round(jd - J2000 + longitude / 360.0);
        double jStar = n - longitude / 360.0;

        double m = Math.toRadians((357.5291 + 0.98560028 * jStar) % 360.0);
        double c = 1.9148 * Math.sin(m) + 0.02 * Math.sin(2 * m) + 0.0003 * Math.sin(3 * m);
        double lambda = Math.toRadians((Math.toDegrees(m) + c + 180.0 + 102.9372) % 360.0);
        double jTransit = J2000 + jStar + 0.0053 * Math.sin(m) - 0.0069 * Math.sin(2 * lambda);

        double sinDec = Math.sin(lambda) * Math.sin(Math.toRadians(23.4397));
        double cosDec = Math.cos(Math.asin(sinDec));
        double phi = Math.toRadians(latitude);
        double cosW = (Math.sin(Math.toRadians(-0.833)) - Math.sin(phi) * sinDec)
                / (Math.cos(phi) * cosDec);
        if (Double.isNaN(cosW) || cosW < -1.0 || cosW > 1.0) {
            return null;
        }
        double w = Math.toDegrees(Math.acos(cosW)) / 360.0;
        return new long[] { toMillis(jTransit - w), toMillis(jTransit + w) };
    }

    private static long toMillis(double jd) {
        return Math.round((jd - JD_UNIX_EPOCH) * MS_PER_DAY);
    }
}
