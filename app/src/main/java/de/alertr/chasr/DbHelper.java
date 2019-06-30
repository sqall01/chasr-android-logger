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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Database helper
 *
 */

class DbHelper extends SQLiteOpenHelper {

    private static DbHelper sInstance;

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "scoutr.db";

    private static final String SQL_CREATE_POSITIONS =
            "CREATE TABLE " + DbContract.Positions.TABLE_NAME + " (" +
            DbContract.Positions._ID + " INTEGER PRIMARY KEY," +
            DbContract.Positions.COLUMN_TIME + " TEXT," +
            DbContract.Positions.COLUMN_LATITUDE + " TEXT," +
            DbContract.Positions.COLUMN_LONGITUDE + " TEXT," +
            DbContract.Positions.COLUMN_ALTITUDE + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_BEARING + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_SPEED + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_ACCURACY + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_PROVIDER + " TEXT," +
            DbContract.Positions.COLUMN_SYNCED + " INTEGER DEFAULT 0," +
            DbContract.Positions.COLUMN_ERROR + " TEXT DEFAULT NULL)";
    private static final String SQL_CREATE_DEVICENAME =
            "CREATE TABLE " + DbContract.Devicename.TABLE_NAME + " (" +
                    DbContract.Devicename.COLUMN_ID + " INTEGER DEFAULT NULL," +
                    DbContract.Devicename.COLUMN_NAME + " TEXT)";

    private static final String SQL_CREATE_SYNC =
            "CREATE TABLE " + DbContract.Sync.TABLE_NAME + " (" +
                    DbContract.Sync.COLUMN_ID + " INTEGER DEFAULT NULL," +
                    DbContract.Sync.COLUMN_TIME + " TEXT)";

    private static final String SQL_DELETE_POSITIONS =
            "DROP TABLE IF EXISTS " + DbContract.Positions.TABLE_NAME;
    private static final String SQL_DELETE_TRACK =
            "DROP TABLE IF EXISTS " + DbContract.Devicename.TABLE_NAME;
    private static final String SQL_DELETE_SYNC =
            "DROP TABLE IF EXISTS " + DbContract.Sync.TABLE_NAME;

    /**
     * Private constructor
     *
     * @param context Context
     */
    private DbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Get DbHelper singleton instance
     *
     * @param context Context
     * @return DbHelper instance
     */
    static DbHelper getInstance(Context context) {

        if (sInstance == null) {
            sInstance = new DbHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Create track and positions tables
     * @param db Database handle
     */
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_POSITIONS);
        db.execSQL(SQL_CREATE_DEVICENAME);
        db.execSQL(SQL_CREATE_SYNC);
    }

    /**
     * On upgrade delete all tables, call create
     * @param db Database handle
     * @param oldVersion Old version number
     * @param newVersion New version number
     */
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_POSITIONS);
        db.execSQL(SQL_DELETE_TRACK);
        db.execSQL(SQL_DELETE_SYNC);
        onCreate(db);
    }

    /**
     * On downgrade behave as on upgrade
     * @param db Database handle
     * @param oldVersion Old version number
     * @param newVersion New version number
     */
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
