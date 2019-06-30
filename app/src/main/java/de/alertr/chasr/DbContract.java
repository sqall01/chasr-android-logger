/*
 * written by sqall
 * Twitter: https://twitter.com/sqall01
 * Blog: https://h4des.org
 * Github: https://github.com/sqall01
 * Github Repository: https://github.com/sqall01/chasr-android-logger
 *
 * original from https://github.com/bfabiszewski/ulogger-android by Bartek Fabiszewski
 *
 * This file is part of Chasr Android Logger.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package de.alertr.chasr;

import android.provider.BaseColumns;

/**
 * Database contract
 *
 */

final class DbContract {

    private DbContract() {}

    /** Positions table */
    public static class Positions implements BaseColumns {
        public static final String TABLE_NAME = "positions";
        public static final String COLUMN_TIME = "time";
        public static final String COLUMN_LONGITUDE = "longitude";
        public static final String COLUMN_LATITUDE = "latitude";
        public static final String COLUMN_ALTITUDE = "altitude";
        public static final String COLUMN_ACCURACY = "accuracy";
        public static final String COLUMN_SPEED = "speed";
        public static final String COLUMN_BEARING = "bearing";
        public static final String COLUMN_PROVIDER = "provider";
        public static final String COLUMN_SYNCED = "synced";
        public static final String COLUMN_ERROR = "error";
    }

    /** Devicename table */
    public static class Devicename {
        public static final String TABLE_NAME = "devicename";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_NAME = "name";
    }

    /** Sync table */
    public static class Sync {
        public static final String TABLE_NAME = "sync";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_TIME = "time";
    }
}
