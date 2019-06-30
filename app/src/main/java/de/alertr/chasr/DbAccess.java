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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Gateway class for database access
 *
 */

class DbAccess {

    private static int openCount;
    private static DbAccess sInstance;

    private static SQLiteDatabase db;
    private static DbHelper mDbHelper;
    private static final String TAG = DbAccess.class.getSimpleName();

    /**
     * Private constructor
     */
    private DbAccess() {
    }

    /**
     * Get singleton instance
     * @return DbAccess singleton
     */
    static synchronized DbAccess getInstance() {
        if (sInstance == null) {
            sInstance = new DbAccess();
        }
        return sInstance;
    }

    /**
     * Opens database
     * @param context Context
     */
    void open(Context context) {
        synchronized (DbAccess.class) {
            if (openCount++ == 0) {
                if (Logger.DEBUG) { Log.d(TAG, "[open]"); }
                mDbHelper = DbHelper.getInstance(context.getApplicationContext());
                db = mDbHelper.getWritableDatabase();
            }
            if (Logger.DEBUG) { Log.d(TAG, "[+openCount = " + openCount + "]"); }
        }
    }

    /**
     * Opens database if it is closed
     * @param context Context
     */
    void openIfClosed(Context context) {
        if(!db.isOpen()) {
            open(context);
        }
    }

    /**
     * Truncates GPS data value to supported size.
     *
     * @param value GPS data value
     * @return String of correct size
     */
    private static String truncGpsValue(double value) {
        // Allow only a precision of 14 characters for the gps data
        // (usual precision is 12 characters).
        String temp = Double.toString(value);
        return temp.substring(0, Math.min(temp.length(), 14));
    }

    /**
     * Write location to database.
     *
     * @param loc Location
     */
    void writeLocation(Location loc) {
        if (Logger.DEBUG) { Log.d(TAG, "[writeLocation]"); }
        ContentValues values = new ContentValues();
        values.put(DbContract.Positions.COLUMN_TIME, loc.getTime() / 1000);
        values.put(DbContract.Positions.COLUMN_LATITUDE, truncGpsValue(loc.getLatitude()));
        values.put(DbContract.Positions.COLUMN_LONGITUDE, truncGpsValue(loc.getLongitude()));
        if (loc.hasBearing()) {
            values.put(DbContract.Positions.COLUMN_BEARING, loc.getBearing());
        }
        if (loc.hasAltitude()) {
            values.put(DbContract.Positions.COLUMN_ALTITUDE, truncGpsValue(loc.getAltitude()));
        }
        else {
            values.put(DbContract.Positions.COLUMN_ALTITUDE, "0.0");
        }
        if (loc.hasSpeed()) {
            values.put(DbContract.Positions.COLUMN_SPEED, truncGpsValue(loc.getSpeed()));
        }
        else {
            values.put(DbContract.Positions.COLUMN_SPEED, "0.0");
        }
        if (loc.hasAccuracy()) {
            values.put(DbContract.Positions.COLUMN_ACCURACY, loc.getAccuracy());
        }
        values.put(DbContract.Positions.COLUMN_PROVIDER, loc.getProvider());

        db.insert(DbContract.Positions.TABLE_NAME, null, values);
    }

    /**
     * Write sync timestamp to database.
     *
     * @param timestamp timestamp of last sync
     */
    void writeSyncTime(long timestamp) {
        if (Logger.DEBUG) { Log.d(TAG, "[writeSyncTime]"); }
        db.delete(DbContract.Sync.TABLE_NAME, null, null);
        ContentValues values = new ContentValues();
        values.put(DbContract.Sync.COLUMN_TIME, timestamp);
        db.insert(DbContract.Sync.TABLE_NAME, null, values);
    }

    /**
     * Get result set containing all positions.
     *
     * @return Result set
     */
    Cursor getPositions() {
        return db.query(DbContract.Positions.TABLE_NAME,
                new String[] {"*"},
                null, null, null, null,
                DbContract.Positions._ID);
    }

    /**
     * Get result set containing positions marked as not synchronized.
     *
     * @return Result set
     */
    Cursor getUnsynced() {
        return db.query(DbContract.Positions.TABLE_NAME,
                new String[] {"*"},
                DbContract.Positions.COLUMN_SYNCED + "=?",
                new String[] {"0"},
                null, null,
                DbContract.Positions._ID);
    }

    /**
     * Deletes all synced positions.
     */
    void deleteSynced() {
        db.delete(DbContract.Positions.TABLE_NAME,
                DbContract.Positions.COLUMN_SYNCED + "=?",
                new String[] {"1"});
    }

    /**
     * Get error message from first not synchronized position.
     *
     * @return Error message or null if none
     */
    String getError() {
        Cursor query = db.query(DbContract.Positions.TABLE_NAME,
                new String[] {DbContract.Positions.COLUMN_ERROR},
                DbContract.Positions.COLUMN_SYNCED + "=?",
                new String[] {"0"},
                null, null,
                DbContract.Positions._ID,
                "1");
        String error = null;
        if (query.moveToFirst()) {
            error = query.getString(0);
        }
        query.close();
        return error;
    }

    /**
     * Add error message to first not synchronized position.
     *
     * @param error Error message
     */
    void setError(String error) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Positions.COLUMN_ERROR, error);
        db.update(DbContract.Positions.TABLE_NAME,
                values,
                DbContract.Positions._ID +
                        "=(SELECT MIN(" + DbContract.Positions._ID + ") " +
                        "FROM " + DbContract.Positions.TABLE_NAME + " " +
                        "WHERE " + DbContract.Positions.COLUMN_SYNCED + "=?)",
                new String[] { "0" });
    }

    /**
     * Mark position as synchronized.
     *
     * @param id Position id
     */
    void setSynced(int id) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Positions.COLUMN_SYNCED, "1");
        values.putNull(DbContract.Positions.COLUMN_ERROR);
        db.update(DbContract.Positions.TABLE_NAME,
                values,
                DbContract.Positions._ID + "=?",
                new String[] { String.valueOf(id) });
    }

    /**
     * Get number of not synchronized items.
     *
     * @return Count
     */
    int countUnsynced() {
        Cursor count = db.query(DbContract.Positions.TABLE_NAME,
                new String[] {"COUNT(*)"},
                DbContract.Positions.COLUMN_SYNCED + "=?",
                new String[] {"0"},
                null, null, null);
        int result = 0;
        if (count.moveToFirst()) {
            result = count.getInt(0);
        }
        count.close();
        return result;
    }

    /**
     * Checks if database needs synchronization,
     * i.e. contains non-synchronized positions.
     *
     * @return True if synchronization needed, false otherwise
     */
    boolean needsSync() {
        return (countUnsynced() > 0);
    }

    /**
     * Get first saved location time.
     *
     * @return UTC timestamp in seconds
     */
    long getFirstTimestamp() {
        Cursor query = db.query(DbContract.Positions.TABLE_NAME,
                new String[] {DbContract.Positions.COLUMN_TIME},
                null, null, null, null,
                DbContract.Positions._ID + " ASC",
                "1");
        long timestamp = 0;
        if (query.moveToFirst()) {
            timestamp = query.getInt(0);
        }
        query.close();
        return timestamp;
    }

    /**
     * Get last saved location time.
     *
     * @return UTC timestamp in seconds
     */
    long getLastTimestamp() {
        Cursor query = db.query(DbContract.Positions.TABLE_NAME,
                new String[] {DbContract.Positions.COLUMN_TIME},
                null, null, null, null,
                DbContract.Positions._ID + " DESC",
                "1");
        long timestamp = 0;
        if (query.moveToFirst()) {
            timestamp = query.getInt(0);
        }
        query.close();
        return timestamp;
    }

    /**
     * Get current device name.
     *
     * @return Device name, null if no devicename in database
     */
    String getDevicename() {
        Cursor query = db.query(DbContract.Devicename.TABLE_NAME,
                new String[] {DbContract.Devicename.COLUMN_NAME},
                null, null, null, null, null,
                "1");
        String devicename = null;
        if (query.moveToFirst()) {
            devicename = query.getString(0);
        }
        query.close();
        return devicename;
    }

    /**
     * Get last sync timestamp.
     *
     * @return Last sync timestamp, 0 if no was in database
     */
    long getSyncTime() {
        Cursor query = db.query(DbContract.Sync.TABLE_NAME,
                new String[] {DbContract.Sync.COLUMN_TIME},
                null, null, null, null, null,
                "1");
        String temp = "0";
        if (query.moveToFirst()) {
            temp = query.getString(0);
        }
        query.close();
        return Long.parseLong(temp);
    }


    /**
     * Set new devicename.
     * Deletes all previous devicename's data and positions. Adds new devicename.
     *
     * @param name New Devicename
     */
    void newDevicename(String name) {
        truncateDevicename();
        truncatePositions();
        ContentValues values = new ContentValues();
        values.put(DbContract.Devicename.COLUMN_NAME, name);
        db.insert(DbContract.Devicename.TABLE_NAME, null, values);
    }

    /**
     * Deletes all devicename metadata.
     */
    private void truncateDevicename() {
        db.delete(DbContract.Devicename.TABLE_NAME, null, null);
    }

    /**
     * Deletes all positions
     */
    private void truncatePositions() {
        db.delete(DbContract.Positions.TABLE_NAME, null, null);
    }

    /**
     * Closes database
     */
    void close() {
        synchronized (DbAccess.class) {
            if (--openCount == 0) {
                if (Logger.DEBUG) { Log.d(TAG, "[close]"); }

                if (db != null) {
                    db.close();
                }
                if (mDbHelper != null) {
                    mDbHelper.close();
                }
            }
            if (Logger.DEBUG) { Log.d(TAG, "[-openCount = " + openCount + "]"); }
        }
    }

    /**
     * Get accuracy from positions cursor
     * @param cursor Cursor
     * @return String accuracy
     */
    static String getAccuracy(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_ACCURACY));
    }

    /**
     * Check if cursor contains accuracy data
     * @param cursor Cursor
     * @return True if has accuracy data
     */
    static boolean hasAccuracy(Cursor cursor) {
        return !cursor.isNull(cursor.getColumnIndex(DbContract.Positions.COLUMN_ACCURACY));
    }

    /**
     * Get speed from positions cursor
     * @param cursor Cursor
     * @return String speed
     */
    static String getSpeed(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_SPEED));
    }

    /**
     * Check if cursor contains speed data
     * @param cursor Cursor
     * @return True if has speed data
     */
    static boolean hasSpeed(Cursor cursor) {
        return !cursor.isNull(cursor.getColumnIndex(DbContract.Positions.COLUMN_SPEED));
    }

    /**
     * Get bearing from positions cursor
     * @param cursor Cursor
     * @return String bearing
     */
    static String getBearing(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_BEARING));
    }

    /**
     * Check if cursor contains bearing data
     * @param cursor Cursor
     * @return True if has bearing data
     */
    static boolean hasBearing(Cursor cursor) {
        return !cursor.isNull(cursor.getColumnIndex(DbContract.Positions.COLUMN_BEARING));
    }

    /**
     * Get altitude from positions cursor
     * @param cursor Cursor
     * @return String altitude
     */
    static String getAltitude(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_ALTITUDE));
    }

    /**
     * Check if cursor contains altitude data
     * @param cursor Cursor
     * @return True if has altitude data
     */
    static boolean hasAltitude(Cursor cursor) {
        return !cursor.isNull(cursor.getColumnIndex(DbContract.Positions.COLUMN_ALTITUDE));
    }

    /**
     * Get provider from positions cursor
     * @param cursor Cursor
     * @return String provider
     */
    static String getProvider(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_PROVIDER));
    }

    /**
     * Check if cursor contains provider data
     * @param cursor Cursor
     * @return True if has provider data
     */
    static boolean hasProvider(Cursor cursor) {
        return !cursor.isNull(cursor.getColumnIndex(DbContract.Positions.COLUMN_PROVIDER));
    }

    /**
     * Get latitude from positions cursor
     * @param cursor Cursor
     * @return String latitude
     */
    static String getLatitude(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_LATITUDE));
    }

    /**
     * Get longitude from positions cursor
     * @param cursor Cursor
     * @return String longitude
     */
    static String getLongitude(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_LONGITUDE));
    }

    /**
     * Get time from positions cursor
     * @param cursor Cursor
     * @return String time
     */
    static String getTime(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_TIME));
    }

    /**
     * Get ISO 8601 formatted time from positions cursor
     * @param cursor Cursor
     * @return String time
     */
    static String getTimeISO8601(Cursor cursor) {
        long timestamp = cursor.getLong(cursor.getColumnIndex(DbContract.Positions.COLUMN_TIME));
        return getTimeISO8601(timestamp);
    }

    /**
     * Get ID from positions cursor
     * @param cursor Cursor
     * @return String ID
     */
    static String getID(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(DbContract.Positions._ID));
    }

    /**
     * Format unix timestamp as ISO 8601 time
     * @param timestamp Timestamp
     * @return Formatted time
     */
    static String getTimeISO8601(long timestamp) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(timestamp * 1000);
    }
}
