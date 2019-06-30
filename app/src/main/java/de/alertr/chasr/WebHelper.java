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
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

/**
 * Web server communication
 *
 */

class WebHelper {
    private static final String TAG = WebSyncService.class.getSimpleName();

    private static String host;
    private static String user;
    private static String pass;
    private static String secret;
    private static byte[] encryption_key;

    private static final String PARAM_ACTION = "action";

    // position
    static final String PARAM_DEVICENAME = "device_name";
    static final String PARAM_IV = "iv";
    static final String PARAM_TIME = "utctime";
    static final String PARAM_LAT = "lat";
    static final String PARAM_LON = "lon";
    static final String PARAM_ALT = "alt";
    static final String PARAM_SPEED = "speed";

    // auth
    private static final String PARAM_USER = "user";
    private static final String PARAM_PASS = "password";

    // gps data
    private static final String PARAM_GPSDATA = "gps_data";

    // encryption
    // Although we are using PKCS7 padding, java uses wrong terminology here
    // because AES cannot be used with PKCS5 (only 8 byte block ciphers) and does PKCS7.
    private static final String ENC_CIPHER = "AES/CBC/PKCS5Padding";
    private static final String ENC_CIPHER_SHORT = "AES";

    // data authentication
    private static final String SIGN_CIPHER = "HmacSHA256";

    private final String userAgent;
    private final Context context;

    // Socket timeout in milliseconds
    static final int SOCKET_TIMEOUT = 30 * 1000;


    /**
     * Constructor
     * @param ctx Context
     */
    WebHelper(Context ctx) {
        context = ctx;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        user = prefs.getString("prefUsername", "");
        pass = prefs.getString("prefPass", "");
        secret = prefs.getString("prefSecret", "");
        host = prefs.getString("prefHost", "").replaceAll("/+$", "");
        userAgent = context.getString(R.string.app_name_ascii) + "/" + BuildConfig.VERSION_NAME + "; " + System.getProperty("http.agent");
    }

    /**
     * Send post request
     * @param params Request parameters
     * @return Server response
     * @throws IOException Connection error
     */
    @SuppressWarnings("StringConcatenationInLoop")
    private String postWithParams(Map<String, String> params) throws IOException {

        URL url = new URL(host + "/submit.php");
        if (Logger.DEBUG) { Log.d(TAG, "[postWithParams: " + url + " : " + params + "]"); }
        String response;

        // Encode data for POST request (key1=value1&key2=value2&...).
        String dataString = "";
        for (Map.Entry<String, String> p : params.entrySet()) {
            String key = p.getKey();
            String value = p.getValue();
            if (dataString.length() > 0) {
                dataString += "&";
            }
            dataString += URLEncoder.encode(key, "UTF-8") + "=";
            dataString += URLEncoder.encode(value, "UTF-8");
        }
        byte[] data = dataString.getBytes();

        // Perform https request.
        HttpsURLConnection connection = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            boolean redirect;
            int redirectTries = 5;
            do {
                redirect = false;
                connection = (HttpsURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("Content-Length", Integer.toString(data.length));
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(SOCKET_TIMEOUT);
                connection.setReadTimeout(SOCKET_TIMEOUT);
                connection.setUseCaches(true);

                out = new BufferedOutputStream(connection.getOutputStream());
                out.write(data);
                out.flush();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpsURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpsURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpsURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307) {
                    URL base = connection.getURL();
                    String location = connection.getHeaderField("Location");
                    if (Logger.DEBUG) { Log.d(TAG, "[postWithParams redirect: " + location + "]"); }
                    if (location == null || redirectTries == 0) {
                        throw new IOException(context.getString(R.string.e_illegal_redirect, responseCode));
                    }
                    redirect = true;
                    redirectTries--;
                    url = new URL(base, location);
                    String h1 = base.getHost();
                    String h2 = url.getHost();
                    if (h1 != null && !h1.equalsIgnoreCase(h2)) {
                        throw new IOException(context.getString(R.string.e_illegal_redirect, responseCode));
                    }
                    try {
                        out.close();
                        connection.getInputStream().close();
                        connection.disconnect();
                    } catch (final IOException e) {
                        if (Logger.DEBUG) { Log.d(TAG, "[connection cleanup failed (ignored)]"); }
                    }
                }
                else if (responseCode != HttpsURLConnection.HTTP_OK) {
                    throw new IOException(context.getString(R.string.e_http_code, responseCode));
                }
            } while (redirect);

            in = new BufferedInputStream(connection.getInputStream());

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine);
            }
            response = sb.toString();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (final IOException e) {
                if (Logger.DEBUG) { Log.d(TAG, "[connection cleanup failed (ignored)]"); }
            }
        }
        if (Logger.DEBUG) { Log.d(TAG, "[postWithParams response: " + response + "]"); }
        return response;

    }

    /**
     * Upload position to server
     * @param unenc_gps_data Map of gps data (still unencrypted)
     * @throws IOException Errors that can occur
     */
    void postPosition(Map<String, String> unenc_gps_data) throws IOException {
        if (Logger.DEBUG) { Log.d(TAG, "[postPosition]"); }

        // Only submit position if everything is configured.
        if(user.equals("")) {
            throw new IOException(context.getString(R.string.e_username));
        }
        else if(pass.equals("")) {
            throw new IOException(context.getString(R.string.e_password));
        }
        else if(secret.equals("")) {
            throw new IOException(context.getString(R.string.e_secret));
        }

        // Create encryption key from secret.
        encryption_key = new byte[32];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            encryption_key = digest.digest(secret.getBytes());
        }
        catch (Throwable e) {
            if (Logger.DEBUG) { Log.d(TAG, "[postPosition key failed: " + e + "]"); }
            throw new IOException(context.getString(R.string.e_key));
        }

        // Create authentication tag.
        String authtag = signGpsData(unenc_gps_data);

        // Encrypt GPS data according to protocol.
        Map<String, String> enc_gps_data = encryptGpsData(unenc_gps_data);
        enc_gps_data.put("authtag", authtag);
        JSONObject gps_data = new JSONObject(enc_gps_data);

        Map<String, String> params = new HashMap<>();
        params.put(PARAM_USER, user);
        params.put(PARAM_PASS, pass);
        params.put(PARAM_GPSDATA, "[" + gps_data.toString() + "]");

        String response = postWithParams(params);
        int code = -1;
        try {
            JSONObject json = new JSONObject(response);
            code = json.getInt("code");
        } catch (JSONException e) {
            if (Logger.DEBUG) {
                Log.d(TAG, "[postPosition json failed: " + e + "]");
            }
        }
        if (code == WebErrorCodes.NO_ERROR) {
            return;
        }
        else if (code == WebErrorCodes.DATABASE_ERROR) {
            throw new IOException(context.getString(R.string.e_database_error));
        }
        else if (code == WebErrorCodes.AUTH_ERROR) {
            throw new IOException(context.getString(R.string.e_auth_error));
        }
        else if (code == WebErrorCodes.ILLEGAL_MSG_ERROR) {
            throw new IOException(context.getString(R.string.e_illegal_msg_error));
        }
        else if (code == WebErrorCodes.SESSION_EXPIRED) {
            throw new IOException(context.getString(R.string.e_session_expired));
        }
        else if (code == WebErrorCodes.ACL_ERROR) {
            throw new IOException(context.getString(R.string.e_acl_error));
        }
        else if(code == -1) {
            throw new IOException(context.getString(R.string.e_unknown_server));
        }
        else {
            throw new IOException(context.getString(R.string.e_unknown));
        }
    }

    /**
     * Converts bytes array to hex string.
     * @param bytes bytes to convert
     * @return String of bytes as hex value
     */
    private static String toHexString(byte[] bytes) {
        Formatter formatter = new Formatter();
        for(byte b : bytes) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    /**
     * Encrypt gps data.
     * @param unenc_gps_data Map of gps data (unencrypted)
     * @throws IOException Errors that can occur
     * @return Map of gps data (encrypted)
     */
    private Map<String, String> encryptGpsData(Map<String, String> unenc_gps_data) throws IOException {

        // Create IV.
        SecureRandom rng = new SecureRandom();
        byte[] iv = new byte[16];
        rng.nextBytes(iv);

        // Encrypt data.
        Map<String, String> enc_gps_data = new HashMap<>();
        enc_gps_data.put("iv", toHexString(iv));
        for(Map.Entry<String, String> entry : unenc_gps_data.entrySet()) {

            if(entry.getKey().equals(PARAM_DEVICENAME) || entry.getKey().equals(PARAM_TIME)) {
                enc_gps_data.put(entry.getKey(), entry.getValue());
                continue;
            }

            try {
                SecretKeySpec skeySpec = new SecretKeySpec(encryption_key, ENC_CIPHER_SHORT);
                Cipher cipher = Cipher.getInstance(ENC_CIPHER);
                cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(iv));
                byte[] encrypted = cipher.doFinal(entry.getValue().getBytes());

                enc_gps_data.put(entry.getKey(), toHexString(encrypted));

            } catch (Throwable e) {
                if (Logger.DEBUG) {
                    Log.d(TAG, "[encryptGpsData encryption failed: " + e + "]");
                }
                throw new IOException(context.getString(R.string.e_encryption));
            }
        }

        return enc_gps_data;
    }

    /**
     * Signs gps data.
     * @param unenc_gps_data Map of gps data (unencrypted)
     * @throws IOException Errors that can occur
     * @return Authentication tag
     */
    private String signGpsData(Map<String, String> unenc_gps_data) throws IOException {

        byte[] authtag;
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(encryption_key, SIGN_CIPHER);
            Mac mac = Mac.getInstance(SIGN_CIPHER);
            mac.init(skeySpec);

            String key_order[] = {"device_name", "utctime", "lat", "lon", "alt", "speed"};
            for(String key : key_order) {
                String element = unenc_gps_data.get(key);
                mac.update(element.getBytes());
            }

            authtag = mac.doFinal();

        } catch (Throwable e) {
            if (Logger.DEBUG) {
                Log.d(TAG, "[signGpsData signing failed: " + e + "]");
            }
            throw new IOException(context.getString(R.string.e_signing));
        }

        return toHexString(authtag);
    }

    /**
     * Check whether given url is valid.
     * Uses relaxed pattern (@see WebPatterns#WEB_URL_RELAXED)
     * @param url URL
     * @return True if valid, false otherwise
     */
    static boolean isValidURL(String url) {
        return WebPatterns.WEB_URL_RELAXED.matcher(url).matches();
    }

}
