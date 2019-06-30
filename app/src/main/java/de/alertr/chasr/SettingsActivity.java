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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

/**
 * Adds preferences from xml resource
 *
 */

public class SettingsActivity extends PreferenceActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    private static Preference prefUsername = null;
    private static Preference prefPass = null;
    private static Preference prefSecret = null;
    private static Preference prefHost = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onCreatePreferenceFragment();
    }

    private void onCreatePreferenceFragment() {
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new MyPreferenceFragment())
                .commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            prefUsername = findPreference("prefUsername");
            prefPass = findPreference("prefPass");
            prefSecret = findPreference("prefSecret");
            prefHost = findPreference("prefHost");
            setListeners();
        }
    }

    /**
     * Set various listeners
     */
    private static void setListeners() {

        if (prefUsername != null) {
            prefUsername.setOnPreferenceChangeListener(serverSetupChanged);
        }
        if (prefPass != null) {
            prefPass.setOnPreferenceChangeListener(serverSetupChanged);
        }
        if (prefSecret != null) {
            prefSecret.setOnPreferenceChangeListener(serverSetupChanged);
        }
        if (prefHost != null) {
            prefHost.setOnPreferenceChangeListener(serverSetupChanged);
        }
        // on click listeners
        if (prefUsername != null) {
            prefUsername.setOnPreferenceClickListener(serverSetupClicked);
        }
        if (prefPass != null) {
            prefPass.setOnPreferenceClickListener(serverSetupClicked);
        }
        if (prefSecret != null) {
            prefPass.setOnPreferenceClickListener(serverSetupClicked);
        }
        if (prefHost != null) {
            prefHost.setOnPreferenceClickListener(serverSetupClicked);
        }
    }

    /**
     * On change listener.
     */
    private final static Preference.OnPreferenceChangeListener serverSetupChanged = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            return true;
        }

    };

    /**
     * On click listener.
     */
    private final static Preference.OnPreferenceClickListener serverSetupClicked = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            return true;
        }

    };

    /**
     * Check whether server setup parameters are set
     * @param context Context
     * @return boolean True if set
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isValidServerSetup(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String host = prefs.getString("prefHost", null);
        final String user = prefs.getString("prefUsername", null);
        final String pass = prefs.getString("prefPass", null);
        final String secret = prefs.getString("prefSecret", null);
        return ((host != null && !host.isEmpty())
                && (user != null && !user.isEmpty())
                && (pass != null && !pass.isEmpty())
                && (secret != null && !secret.isEmpty()));
    }
}
