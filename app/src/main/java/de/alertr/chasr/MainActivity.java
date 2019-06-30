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

import android.Manifest;
import android.location.Location;
import android.support.v7.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static de.alertr.chasr.Alert.*;

/**
 * Main activity of chasr
 *
 */

public class MainActivity extends AppCompatActivity {

    public static final String UPDATED_PREFS = "extra_updated_prefs";

    private final String TAG = MainActivity.class.getSimpleName();

    private final static int LED_GREEN = 1;
    private final static int LED_RED = 2;
    private final static int LED_YELLOW = 3;

    private final static int PERMISSION_LOCATION = 1;
    private final static int RESULT_PREFS_UPDATED = 1;

    private long pref_minTimeMillis;
    private String pref_devicename = "";
    private boolean pref_username_set;
    private boolean pref_password_set;
    private boolean pref_secret_set;

    private static boolean syncError = false;
    private boolean isUploading = false;
    private TextView syncErrorLabel;
    private TextView syncLabel;
    private TextView syncTimeLabel;
    private TextView syncLed;
    private TextView locLabel;
    private TextView locCoord;
    private TextView locLed;

    private DbAccess db = null;
    private static String TXT_START;
    private static String TXT_STOP;
    private Button toggleButton;

    /**
     * Initialization
     * @param savedInstanceState Saved state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updatePreferences();
        TXT_START = getString(R.string.button_start);
        TXT_STOP = getString(R.string.button_stop);
        setContentView(R.layout.activity_main);
        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        toggleButton = findViewById(R.id.toggle_button);
        syncErrorLabel = findViewById(R.id.sync_error);
        syncLabel = findViewById(R.id.sync_status);
        syncTimeLabel = findViewById(R.id.sync_timestamp);
        syncLed = findViewById(R.id.sync_led);
        locLabel = findViewById(R.id.location_status);
        locCoord = findViewById(R.id.location_coordinates);
        locLed = findViewById(R.id.loc_led);
    }

    /**
     * On resume
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (Logger.DEBUG) { Log.d(TAG, "[onResume]"); }

        // If needed chasr settings are not configured, open settings.
        if(!pref_username_set
                || !pref_password_set
                || !pref_secret_set
                || pref_devicename.equals("")) {
            showGoSettings();
            showToast(getString(R.string.provide_chasr_settings), Toast.LENGTH_LONG);
        }

        db = DbAccess.getInstance();
        db.open(this);
        String devicename = db.getDevicename();

        // Set device name if not created yet.
        if (devicename == null || devicename.equals("")) {
            db.newDevicename(pref_devicename);
            devicename = db.getDevicename();
        }

        if (devicename != null) {
            updateDevicenameLabel(devicename);
        }

        if (LoggerService.isRunning()) {
            toggleButton.setText(TXT_STOP);
            setLocLed(LED_GREEN);
        } else {
            toggleButton.setText(TXT_START);
            setLocLed(LED_RED);
        }
        registerBroadcastReceiver();
        updateStatus();

        // Start logger service if it is not already running.
        if (!LoggerService.isRunning()) {
            startLogger();
        }
    }

    /**
     * On pause
     */
    @Override
    protected void onPause() {
        if (Logger.DEBUG) { Log.d(TAG, "[onPause]"); }
        unregisterReceiver(mBroadcastReceiver);
        if (db != null) {
            db.close();
        }
        super.onPause();
    }

    /**
     * On destroy
     */
    @Override
    protected void onDestroy() {
        if (Logger.DEBUG) { Log.d(TAG, "[onDestroy]"); }
        super.onDestroy();
    }


    /**
     * Create main menu
     * @param menu Menu
     * @return Always true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Main menu options
     * @param item Selected option
     * @return True if handled
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_settings:
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(i, RESULT_PREFS_UPDATED);
                return true;
            case R.id.menu_about:
                showAbout();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    /**
     * Callback on permission request result
     * Called after user granted/rejected location permission
     *
     * @param requestCode Permission code
     * @param permissions Permissions
     * @param grantResults Result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        // onPause closed db
        db.open(this);
        switch (requestCode) {
            case PERMISSION_LOCATION:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // onPause closed db
                    db.open(this);
                    startLogger();
                }
                break;
        }
        db.close();
    }

    /**
     * Callback on activity result.
     * Called after user updated preferences
     *
     * @param requestCode Activity code
     * @param resultCode Result
     * @param data Data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_PREFS_UPDATED:
                // Preferences updated
                updatePreferences();
                if (LoggerService.isRunning()) {
                    // restart logging
                    Intent intent = new Intent(MainActivity.this, LoggerService.class);
                    intent.putExtra(UPDATED_PREFS, true);
                    startService(intent);
                }
                break;
        }
    }

    /**
     * Reread user preferences
     */
    private void updatePreferences() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        pref_minTimeMillis = Long.parseLong(prefs.getString("prefMinTime", getString(R.string.pref_mintime_default))) * 1000;
        String pref_username = prefs.getString("prefUsername", "");
        pref_username_set = !pref_username.equals("");
        String pref_pass = prefs.getString("prefPass", "");
        pref_password_set = !pref_pass.equals("");
        String pref_secret = prefs.getString("prefSecret", "");
        pref_secret_set = !pref_secret.equals("");

        // Only set device name if it had changed.
        String temp_devicename = prefs.getString("prefDevicename", "");
        if(!pref_devicename.equals(temp_devicename)) {
            pref_devicename = temp_devicename;

            // Update label if we are ready.
            if(findViewById(R.id.devicename_label) != null) {
                updateDevicenameLabel(pref_devicename);
            }

            // Also change device name in database.
            if(!pref_devicename.equals("") && db != null) {
                db.openIfClosed(this);
                db.newDevicename(pref_devicename);
            }
        }
    }

    /**
     * Called when the user clicks the Start/Stop button
     * @param view View
     */
    public void toggleLogging(@SuppressWarnings("UnusedParameters") View view) {
        if (LoggerService.isRunning()) {
            stopLogger();
        } else {
            startLogger();
        }
    }

    /**
     * Start logger service
     */
    private void startLogger() {

        if(!pref_username_set) {
            syncErrorLabel.setText(getString(R.string.e_username));
            setLocLed(LED_RED);
        }
        else if(!pref_password_set) {
            syncErrorLabel.setText(getString(R.string.e_password));
            setLocLed(LED_RED);
        }
        else if(!pref_secret_set) {
            syncErrorLabel.setText(getString(R.string.e_secret));
            setLocLed(LED_RED);
        }
        else if(pref_devicename.equals("")) {
            syncErrorLabel.setText(getString(R.string.e_devicename));
            setLocLed(LED_RED);
        }
        else {
            // start tracking
            if (db.getDevicename() != null) {
                syncErrorLabel.setText("");
                Intent intent = new Intent(MainActivity.this, LoggerService.class);
                startService(intent);
            } else {
                syncErrorLabel.setText(getString(R.string.e_devicename));
                setLocLed(LED_RED);
            }
        }
    }

    /**
     * Stop logger service
     */
    private void stopLogger() {
        // stop tracking
        Intent intent = new Intent(MainActivity.this, LoggerService.class);
        stopService(intent);
    }

    /**
     * Called when the user clicks the Upload button
     * @param view View
     */
    public void uploadData(@SuppressWarnings("UnusedParameters") View view) {
        if (!SettingsActivity.isValidServerSetup(this)) {
            showToast(getString(R.string.provide_chasr_settings), Toast.LENGTH_LONG);
        } else if (db.needsSync()) {
            Intent syncIntent = new Intent(MainActivity.this, WebSyncService.class);
            startService(syncIntent);
            showToast(getString(R.string.uploading_started));
            isUploading = true;
        } else {
            showToast(getString(R.string.nothing_to_synchronize));
        }
    }

    /**
     * Display toast message
     * @param text Message
     */
    private void showToast(CharSequence text) {
        showToast(text, Toast.LENGTH_SHORT);
    }

    /**
     * Display toast message
     * @param text Message
     * @param duration Duration
     */
    private void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    /**
     * Display About dialog
     */
    private void showAbout() {
        final AlertDialog dialog = showAlert(MainActivity.this,
                getString(R.string.app_name),
                R.layout.about,
                R.drawable.ic_chasr_logo);
        final TextView versionLabel = dialog.findViewById(R.id.about_version);
        versionLabel.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));
        final TextView descriptionLabel = dialog.findViewById(R.id.about_description);
        final TextView description2Label = dialog.findViewById(R.id.about_description2);
        descriptionLabel.setText(getString(R.string.about_description));
        description2Label.setText(getString(R.string.about_description2));
        final Button okButton = dialog.findViewById(R.id.about_button_ok);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    /**
     * Display Go Settings dialog
     */
    private void showGoSettings() {
        final AlertDialog dialog = showAlert(MainActivity.this,
                getString(R.string.app_name),
                R.layout.go_settings,
                R.drawable.ic_chasr_logo);
        final TextView descriptionLabel = dialog.findViewById(R.id.go_settings_description);
        descriptionLabel.setText(getString(R.string.go_settings_description));
        final Button goButton = dialog.findViewById(R.id.go_settings_button);

        goButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(i, RESULT_PREFS_UPDATED);
            }
        });
    }

    /**
     * Depreciated fromHtml method for build version < 24
     * @param text Message text
     * @return Text with parsed html
     */
    @SuppressWarnings("deprecation")
    private static CharSequence fromHtmlDepreciated(String text) {
        return Html.fromHtml(text);
    }

    /**
     * Update device name label
     * @param devicename Device name
     */
    private void updateDevicenameLabel(String devicename) {
        final TextView devicenameLabel = findViewById(R.id.devicename_label);
        devicenameLabel.setText(devicename);
    }

    /**
     * Update location tracking status label
     * @param lastUpdateRealtime Real time of last location update
     */
    private void updateLocationLabel(long lastUpdateRealtime, Location lastLocation) {
        // get last location update time
        String timeString;
        String coordString;
        long timestamp = 0;
        long elapsed = 0;
        long dbTimestamp;
        if (lastUpdateRealtime > 0) {
            elapsed = (SystemClock.elapsedRealtime() - lastUpdateRealtime);
            timestamp = System.currentTimeMillis() - elapsed;
        } else if ((dbTimestamp = db.getLastTimestamp()) > 0) {
            timestamp = dbTimestamp * 1000;
            elapsed = System.currentTimeMillis() - timestamp;
        }

        // Set time of last GPS update.
        if (timestamp > 0) {
            final Date updateDate = new Date(timestamp);
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(updateDate);
            final Calendar today = Calendar.getInstance();
            DateFormat df;
            if (calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                df = DateFormat.getTimeInstance();
            } else {
                df = DateFormat.getDateTimeInstance();
            }
            df.setTimeZone(TimeZone.getDefault());
            timeString = String.format(getString(R.string.label_last_update), df.format(updateDate));
        } else {
            timeString = "-";
        }
        locLabel.setText(timeString);

        // Set coordinates of last GPS update.
        if (lastLocation != null) {
            double lat = lastLocation.getLatitude();
            double lon = lastLocation.getLongitude();
            coordString = String.format(Locale.US, "%.6f", lat);
            coordString += ", ";
            coordString += String.format(Locale.US, "%.6f", lon);
            coordString = String.format(getString(R.string.label_last_coordinates), coordString);
        }
        else {
            coordString = "-";
        }
        locCoord.setText(coordString);

        // Change led if more than 2 update periods elapsed since last location update
        if (LoggerService.isRunning() && (timestamp == 0 || elapsed > pref_minTimeMillis * 2)) {
            setLocLed(LED_YELLOW);
        }
    }

    /**
     * Update synchronization status label and led
     * @param unsynced Count of not synchronized positions
     */
    private void updateSyncStatus(int unsynced, long syncTime) {

        // Set number of positions that need to be synced.
        String text;
        if (unsynced > 0) {
            text = getResources().getQuantityString(R.plurals.label_positions_behind, unsynced, unsynced);
            if (syncError) {
                setSyncLed(LED_RED);
            } else {
                setSyncLed(LED_YELLOW);
            }
        } else {
            text = getString(R.string.label_synchronized);
            setSyncLed(LED_GREEN);
        }
        syncLabel.setText(text);

        // Set last time of synchronization.
        String timeString;
        if (syncTime > 0) {
            final Date updateDate = new Date(syncTime * 1000);
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(updateDate);
            final Calendar today = Calendar.getInstance();
            DateFormat df;
            if (calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                df = DateFormat.getTimeInstance();
            } else {
                df = DateFormat.getDateTimeInstance();
            }
            df.setTimeZone(TimeZone.getDefault());
            timeString = String.format(getString(R.string.label_last_sync), df.format(updateDate));
        } else {
            timeString = "-";
        }
        syncTimeLabel.setText(timeString);

    }

    /**
     * Update location tracking and synchronization status
     */
    private void updateStatus() {
        updateLocationLabel(LoggerService.lastUpdateRealtime(), LoggerService.lastLocation());
        // get sync status
        int count = db.countUnsynced();
        long syncTime = db.getSyncTime();
        String error = db.getError();
        if (error != null) {
            if (Logger.DEBUG) { Log.d(TAG, "[sync error: " + error + "]"); }
            syncError = true;
            syncErrorLabel.setText(error);
        } else if (syncError) {
            syncError = false;
            syncErrorLabel.setText(null);
        }
        updateSyncStatus(count, syncTime);
    }

    /**
     * Set status led color
     * @param led Led text view
     * @param color Color (red, yellow or green)
     */
    private void setLedColor(TextView led, int color) {
        Drawable l;
        l = led.getCompoundDrawablesRelative()[0];
        switch (color) {
            case LED_RED:
                l.setColorFilter(ContextCompat.getColor(this, R.color.colorRed), PorterDuff.Mode.SRC_ATOP);
                break;

            case LED_GREEN:
                l.setColorFilter(ContextCompat.getColor(this, R.color.colorGreen), PorterDuff.Mode.SRC_ATOP);
                break;

            case LED_YELLOW:
                l.setColorFilter(ContextCompat.getColor(this, R.color.colorYellow), PorterDuff.Mode.SRC_ATOP);
                break;
        }
        l.invalidateSelf();
    }

    /**
     * Set synchronization status led color
     * Red - synchronization error
     * Yellow - synchronization delay
     * Green - synchronized
     * @param color Color
     */
    private void setSyncLed(int color) {
        if (Logger.DEBUG) { Log.d(TAG, "[setSyncLed " + color + "]"); }
        setLedColor(syncLed, color);
    }

    /**
     * Set location tracking status led color
     * Red - tracking off
     * Yellow - tracking on, long time since last update
     * Green - tracking on, recently updated
     * @param color Color
     */
    private void setLocLed(int color) {
        if (Logger.DEBUG) { Log.d(TAG, "[setLocLed " + color + "]"); }
        setLedColor(locLed, color);
    }

    /**
     * Register broadcast receiver for synchronization
     * and tracking status updates
     */
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(LoggerService.BROADCAST_LOCATION_STARTED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_STOPPED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_UPDATED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_GPS_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_GPS_ENABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED);
        filter.addAction(WebSyncService.BROADCAST_SYNC_DONE);
        filter.addAction(WebSyncService.BROADCAST_SYNC_FAILED);
        registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Broadcast receiver
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Logger.DEBUG) { Log.d(TAG, "[broadcast received " + intent + "]"); }
            if (intent == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case LoggerService.BROADCAST_LOCATION_UPDATED:
                    updateLocationLabel(LoggerService.lastUpdateRealtime(), LoggerService.lastLocation());
                    setLocLed(LED_GREEN);
                    updateSyncStatus(db.countUnsynced(), db.getSyncTime());
                    break;
                case WebSyncService.BROADCAST_SYNC_DONE:
                    final int unsyncedCount = db.countUnsynced();
                    long syncTime = db.getSyncTime();
                    updateSyncStatus(unsyncedCount, syncTime);
                    setSyncLed(LED_GREEN);
                    // reset error flag and label
                    if (syncError) {
                        syncErrorLabel.setText(null);
                        syncError = false;
                    }
                    // showConfirm message if manual uploading
                    if (isUploading && unsyncedCount == 0) {
                        showToast(getString(R.string.uploading_done));
                        isUploading = false;
                    }
                    db.deleteSynced();
                    break;
                case (WebSyncService.BROADCAST_SYNC_FAILED): {
                    updateSyncStatus(db.countUnsynced(), db.getSyncTime());
                    setSyncLed(LED_RED);
                    // set error flag and label
                    String message = intent.getStringExtra("message");
                    syncErrorLabel.setText(message);
                    syncError = true;
                    // showConfirm message if manual uploading
                    if (isUploading) {
                        showToast(getString(R.string.uploading_failed) + "\n" + message, Toast.LENGTH_LONG);
                        isUploading = false;
                    }
                    break;
                }
                case LoggerService.BROADCAST_LOCATION_STARTED:
                    toggleButton.setText(TXT_STOP);
                    showToast(getString(R.string.tracking_started));
                    setLocLed(LED_YELLOW);
                    break;
                case LoggerService.BROADCAST_LOCATION_STOPPED:
                    toggleButton.setText(TXT_START);
                    showToast(getString(R.string.tracking_stopped));
                    setLocLed(LED_RED);
                    break;
                case LoggerService.BROADCAST_LOCATION_GPS_DISABLED:
                    //showToast(getString(R.string.gps_disabled_warning), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED:
                    //showToast(getString(R.string.net_disabled_warning), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_DISABLED:
                    syncErrorLabel.setText(getString(R.string.location_disabled));
                    setLocLed(LED_RED);
                    break;
                case LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED:
                    //showToast(getString(R.string.using_network), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_GPS_ENABLED:
                    //showToast(getString(R.string.using_gps), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED:
                    showToast(getString(R.string.location_permission_denied), Toast.LENGTH_LONG);
                    syncErrorLabel.setText(getString(R.string.location_permission_denied));
                    setLocLed(LED_RED);
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION);
                    break;
            }
        }
    };
}
