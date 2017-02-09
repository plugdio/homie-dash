package com.plugdio.homiedash;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.DhcpInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.plugdio.homiedash.Data.CupboardSQLiteOpenHelper;
import com.plugdio.homiedash.Data.Device;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;

public class DeviceAdd extends AppCompatActivity {
    private String LOG_TAG = "DeviceAdd";

    private boolean wifiOn = true;

    private String deviceId;
    private EditText eWifiNetwork;
    private EditText eWifiPass;
    private EditText eDeviceFriendlyName;
    private EditText eDeviceDescription;
    private String mqttHost;
    private String mqttPort;
    private boolean mqttAuth;
    private String mqttUser;
    private String mqttPass;
    private String homieBaseTopic;

    private ProgressDialog progress;
    private boolean settingDone = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_add);

        setupActionBar();

        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        String wifiSSID = sharedPrefs.getString("wifi_ssid", "");
        String wifiPass = sharedPrefs.getString("wifi_password", "");
        mqttHost = sharedPrefs.getString("mqtt_broker_address", "");
        mqttPort = sharedPrefs.getString("mqtt_broker_port", "");
        mqttAuth = sharedPrefs.getBoolean("mqtt_authentication_switch", true);
        mqttUser = sharedPrefs.getString("mqtt_username", "");
        mqttPass = sharedPrefs.getString("mqtt_password", "");
        homieBaseTopic = sharedPrefs.getString("homie_base_topic", "");
/*
        boolean homieOtaEnabled = sharedPrefs.getBoolean("ota_switch", false);
        String otaHost = sharedPrefs.getString("ota_host", "");
        String otaPort = sharedPrefs.getString("ota_port", "");
        String otaPath = sharedPrefs.getString("ota_path", "");
*/

        Intent intent = this.getIntent();

        if ((intent != null) && (intent.hasExtra("SSID"))) {

            wifiOn = intent.getBooleanExtra("wifiOn", true);

            String deviceIdPattern = "Homie-(.*)";

            // Create a Pattern object
            Pattern r = Pattern.compile(deviceIdPattern);

            // Now create matcher object.
            Matcher m = r.matcher(intent.getStringExtra("SSID"));
            if (m.find()) {
                deviceId = m.group(1);
            }

            EditText eDeviceId = (EditText) findViewById(R.id.device_id);
            eDeviceId.setText(deviceId);
            eDeviceId.setEnabled(false);

        }

        eDeviceFriendlyName = (EditText) findViewById(R.id.input_friendlyname);
        eDeviceDescription = (EditText) findViewById(R.id.input_description);

        eWifiNetwork = (EditText) findViewById(R.id.input_wifinetwork);
        eWifiNetwork.setText(wifiSSID);

        eWifiPass = (EditText) findViewById(R.id.input_wifipass);
        eWifiPass.setText(wifiPass);

        EditText eMQTTHost = (EditText) findViewById(R.id.input_mqtthost);
        eMQTTHost.setText(mqttHost);

        EditText eMQTTPort = (EditText) findViewById(R.id.input_mqttport);
        eMQTTPort.setText(mqttPort);

        CheckBox cMQTTAuth = (CheckBox) findViewById(R.id.mqtt_authentication);
        cMQTTAuth.setChecked(mqttAuth);

        final EditText eMQTTUser = (EditText) findViewById(R.id.input_mqttuser);

        if (!mqttAuth) {
            eMQTTUser.setEnabled(false);
        } else {
            eMQTTUser.setText(mqttUser);
        }

        EditText eHomeBaseTopic = (EditText) findViewById(R.id.input_homiebasetopic);
        eHomeBaseTopic.setText(homieBaseTopic);

    }

    public void addNewDevice(View v) {
        Log.d(LOG_TAG, "Adding new device");
        /*
        0. save device
        1. change wifi network
        2. send config as json
        3. restore wifi
         */

        String deviceName = eDeviceFriendlyName.getText().toString();
        String deviceDescription = eDeviceDescription.getText().toString();
        String deviceWifiNetwork = eWifiNetwork.getText().toString();
        String deviceWifiPass = eWifiPass.getText().toString();

        CupboardSQLiteOpenHelper dbHelper = new CupboardSQLiteOpenHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // check if i know anything about the device
        Device myDevice = cupboard().withDatabase(db).query(com.plugdio.homiedash.Data.Device.class).withSelection("deviceId = ?", deviceId).get();
        if (myDevice == null) {
            Log.d(LOG_TAG, "Device doesn't exists yet, let's save it");
            myDevice = new Device(deviceId);
            myDevice.infoSource = Device.MANUALLY_ADDED;
            myDevice.type = Device.TYPE_HOMIE;

            myDevice.deviceName = deviceName;
            myDevice.description = deviceDescription;

            long id = cupboard().withDatabase(db).put(myDevice);
            Log.d(LOG_TAG, "Device saved with id: " + id);
/*
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(MQTT_NEW_DEVICE_INTENT);
            broadcastIntent.putExtra(MQTT_MSG_RECEIVED_MSG, myDeviceId);
            sendBroadcast(broadcastIntent);
*/
        } else {
            Log.d(LOG_TAG, "Device exists already, id: " + myDevice._id);
            ContentValues values = new ContentValues(1);
            values.put("deviceName", deviceName);
            values.put("description", deviceDescription);
            cupboard().withDatabase(db).update(Device.class, values, "_id = ?", myDevice._id + "");
        }

        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", "Homie-" + deviceId);
        wifiConfig.preSharedKey = String.format("\"%s\"", deviceId);

        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        int netId = wifiManager.addNetwork(wifiConfig);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();

        progress = ProgressDialog.show(this, "Homie-" + deviceId,
                "Connecting");
        progress.show();

        JSONObject configObj = new JSONObject();
        JSONObject wifiConfigObj = new JSONObject();
        JSONObject mqttConfigObj = new JSONObject();
        JSONObject otaConfigObj = new JSONObject();
        try {
            configObj.put("name", deviceName);
            wifiConfigObj.put("ssid", deviceWifiNetwork);
            wifiConfigObj.put("password", deviceWifiPass);
            configObj.put("wifi", wifiConfigObj);
            mqttConfigObj.put("host", mqttHost);
            mqttConfigObj.put("port", Integer.valueOf(mqttPort));
            mqttConfigObj.put("base_topic", homieBaseTopic);
            mqttConfigObj.put("auth", mqttAuth);
            if (mqttAuth) {
                mqttConfigObj.put("username", mqttUser);
                mqttConfigObj.put("password", mqttPass);
            }
            mqttConfigObj.put("ssl", false);
            configObj.put("mqtt", mqttConfigObj);
            otaConfigObj.put("enabled", false);
            configObj.put("ota", otaConfigObj);

        } catch (JSONException e) {
            Log.e(LOG_TAG, "JSONException: " + e.getMessage());
        }


        new LongOperation().execute(configObj.toString());

    }


    private class LongOperation extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {

            String config = params[0];
            config.replace("\\/", "/");

            Log.d(LOG_TAG, "config: " + config);

            String homieAddress = "192.168.123.1";
            int i = 0;
            boolean wifiConnected = false;
            while ((i < 30) && !wifiConnected) {
//            while (i < 10) {
                try {

                    WifiManager wifiManager = (WifiManager) getSystemService(getApplicationContext().WIFI_SERVICE);
                    WifiInfo info = wifiManager.getConnectionInfo();
                    String ssid = info.getSSID();
                    int ip = info.getIpAddress();
                    String ipAddress = Formatter.formatIpAddress(ip);
                    Log.d(LOG_TAG, i + ". ssid:" + ssid + ", ip: " + ipAddress);

                    if (ssid.equals("\"Homie-" + deviceId + "\"") && (!ipAddress.equals("0.0.0.0"))) {

                        DhcpInfo dhcp = wifiManager.getDhcpInfo();
                        homieAddress = Formatter.formatIpAddress(dhcp.gateway);

                        SocketAddress sockAddress = new InetSocketAddress(homieAddress, 80);
                        // Create an unbound socket
                        Socket sock = new Socket();

                        int timeoutMs = 2000;   // 2 seconds
                        try {
                            sock.connect(sockAddress, timeoutMs);
                            wifiConnected = true;
                        } catch (SocketTimeoutException e) {
                            Log.d(LOG_TAG, "SocketTimeoutException, could not connect to Homie yet: " + e.getMessage());
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "IOException: " + e.getMessage());
                        }

                    }
                    i++;
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "InterruptedException: " + e.getMessage());
                }
            }

            if (!wifiConnected) {
                Log.d(LOG_TAG, "Could not connect to Homie");
                progress.setMessage("Connection failed");
            } else {
                Log.d(LOG_TAG, "Let's talk to Homie");
//                progress.setMessage("Saving configuration");

                try {

                    OkHttpClient client = new OkHttpClient();

                    Request request = new Request.Builder()
                            .url("http://" + homieAddress + "/config")
                            .put(RequestBody
                                    .create(MediaType
                                                    .parse("application/json"),
                                            config
                                    ))
                            .addHeader("content-type", "application/json")
                            .build();

                    Response response = client.newCall(request).execute();
                    Log.d(LOG_TAG, "http responseCode: " + response.code());
                    Log.d(LOG_TAG, "http response: " + response.body().string());

                    if (response.code() == 200) {
                        settingDone = true;
                    }

                } catch (MalformedURLException e) {
                    //e.printStackTrace();
                    Log.e(LOG_TAG, "ProtocolException: " + e.getMessage());
                } catch (ProtocolException e) {
                    //e.printStackTrace();
                    Log.e(LOG_TAG, "ProtocolException: " + e.getMessage());
                } catch (IOException e) {
                    //e.printStackTrace();
                    Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {

            //remove homie network
            WifiManager wifiManager = (WifiManager) getSystemService(getApplicationContext().WIFI_SERVICE);
            List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
            for (WifiConfiguration i : list) {
//                Log.d(LOG_TAG, "network: " + i.SSID + ", " + i.networkId);
                if (i.SSID.equals("\"Homie-" + deviceId + "\"")) {
                    Log.d(LOG_TAG, "removing network: " + i.SSID + ", " + i.networkId);
                    wifiManager.removeNetwork(i.networkId);
                    wifiManager.saveConfiguration();
                }

            }

            progress.dismiss();
            if (settingDone) {
                Toast.makeText(getApplicationContext(), "Device added", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(DeviceAdd.this, MainActivity.class));
            } else {
                Toast.makeText(getApplicationContext(), "Device could not be added", Toast.LENGTH_SHORT).show();
            }

            if (!wifiOn) {
                wifiManager.setWifiEnabled(false);
            }
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
//            actionBar.hide();
        }
    }


}
