package com.plugdio.homiedash;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.plugdio.homiedash.Data.CupboardSQLiteOpenHelper;
import com.plugdio.homiedash.Data.Device;
import com.plugdio.homiedash.Service.HomieDashService;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.qbusict.cupboard.QueryResultIterable;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;

public class MainActivity extends AppCompatActivity {

    final Context context = MainActivity.this;

    private String LOG_TAG = "MainActivity";
    private SQLiteDatabase db;
    public DeviceArrayAdapter deviceAdapter;

    private ImageView alertImage;
    private TextView alertText;

    private int backButtonCount = 0;

    WifiManager wifi;
    boolean wifiScanResultProcessed = false;

    TextView mqttStatusTextView;
    String statusMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        alertImage = (ImageView) findViewById(R.id.alert);
        alertText = (TextView) findViewById(R.id.alert_text);

        mqttStatusTextView = (TextView) findViewById(R.id.mqtt_status);
        startHomieDashService();

        final ListView deviceListView = (ListView) findViewById(R.id.listview_devices);

        ArrayList<Device> deviceEntries = new ArrayList<>();
        deviceAdapter = new DeviceArrayAdapter(this, 0, deviceEntries);


        CupboardSQLiteOpenHelper dbHelper = new CupboardSQLiteOpenHelper(this);
        db = dbHelper.getWritableDatabase();

        Cursor devicesCursor = cupboard().withDatabase(db).query(Device.class).getCursor();
        try {
            QueryResultIterable<Device> itr = cupboard().withCursor(devicesCursor).iterate(Device.class);

            for (Device d : itr) {

                deviceEntries.add(d);
            }
        } finally {
            devicesCursor.close();
        }

        deviceListView.setAdapter(deviceAdapter);

        //add event listener so we can handle clicks
        AdapterView.OnItemClickListener adapterViewListener = new AdapterView.OnItemClickListener() {

            //on click
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Log.d(LOG_TAG, "onItemClick: " + position + " - " + deviceAdapter.getItem(position).deviceId);

                Intent deviceDetailActivity = new Intent(getApplicationContext(), DeviceDetail.class);
                deviceDetailActivity.putExtra(Intent.EXTRA_TEXT, deviceAdapter.getItem(position).deviceId);
                deviceDetailActivity.putExtra(Intent.EXTRA_TITLE, deviceAdapter.getItem(position).deviceName);
                startActivity(deviceDetailActivity);
            }
        };

        //set the listener to the list view
        deviceListView.setOnItemClickListener(adapterViewListener);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifi.isWifiEnabled() == false) {
                    Toast.makeText(getApplicationContext(), "Wifi is disabled. Turning on", Toast.LENGTH_SHORT).show();
                    wifi.setWifiEnabled(true);
                }
                wifiScanResultProcessed = false;

                // register WiFi scan results receiver
                IntentFilter filter = new IntentFilter();
                filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                registerReceiver(wifiReceiver, filter);

                if (wifi.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                    wifi.startScan();
                    Log.d(LOG_TAG, "wifi scanning stared");
                }

                new CountDownTimer(3000, 1000) {

                    public void onTick(long millisUntilFinished) {
                        Log.d(LOG_TAG, "wifi scan seconds remaining: " + millisUntilFinished / 1000);
                    }

                    public void onFinish() {
                        Log.d(LOG_TAG, "wifi scan done!");
                        if (!wifiScanResultProcessed) {
                            Log.d(LOG_TAG, "No wifi found");
                            unregisterReceiver(wifiReceiver);
                            displayWifiList(new ArrayList<String>());
                        }
                    }
                }.start();

            }
        });
    }

    public void EditDevice(View view) {
        Log.d(LOG_TAG, "editdevice - " + view.getId());
        LinearLayout l = (LinearLayout) view.getParent();
        TextView t = (TextView) l.findViewById(R.id.list_item_device_textview);
        Log.d(LOG_TAG, t.getText() + "");
        Toast.makeText(getApplicationContext(), "Edit " + t.getText(), Toast.LENGTH_SHORT).show();
    }

    public void DeleteDevice(View view) {
        Log.d(LOG_TAG, "editdevice - " + view.getId());
        LinearLayout l = (LinearLayout) view.getParent();
        TextView t = (TextView) l.findViewById(R.id.list_item_device_textview);
        Log.d(LOG_TAG, t.getText() + "");
        Toast.makeText(getApplicationContext(), "Delete " + t.getText(), Toast.LENGTH_SHORT).show();
    }

    private BroadcastReceiver mqttStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle notificationData = intent.getExtras();
            HomieDashService.ConnectionStatus statusCode =
                    HomieDashService.ConnectionStatus.class.getEnumConstants()[notificationData.getInt(
                            HomieDashService.MQTT_STATUS_CODE)];
            statusMsg = notificationData.getString(
                    HomieDashService.MQTT_STATUS_MSG);
            mqttStatusTextView.setText(statusMsg);

            Log.d(LOG_TAG, "status: " + statusCode);

            if (statusCode == HomieDashService.ConnectionStatus.NO_CONFIG) {
                alertImage.setVisibility(View.VISIBLE);
                alertText.setText(getString(R.string.alert_missing_config));
                alertText.setVisibility(View.VISIBLE);
            } else {
                alertImage.setVisibility(View.INVISIBLE);
                alertText.setVisibility(View.INVISIBLE);
            }
        }
    };

    private BroadcastReceiver newDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle notificationData = intent.getExtras();
            String deviceId = notificationData.getString(HomieDashService.MQTT_MSG_RECEIVED_MSG);
//TODO: fix this
//            deviceAdapter.add("- (" + deviceId + ")");
            recreate();
        }
    };

    private BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent arg1) {

            String deviceIdPattern = "Homie-(.*)";
            Pattern r = Pattern.compile(deviceIdPattern);

            List<ScanResult> results = wifi.getScanResults();
            List<String> items = new ArrayList<String>();
            int i;
            for (i = 0; i < results.size(); ++i) {
                //items.add(results.get(i).SSID);
                Log.d(LOG_TAG, i + ". wifi network: " + results.get(i).SSID);
                Log.d(LOG_TAG, i + ". wifi network: " + results.get(i).toString());

                Matcher m = r.matcher(results.get(i).SSID);
                if (m.find()) {
                    items.add(results.get(i).SSID);
                }
            }
            wifiScanResultProcessed = true;
            context.unregisterReceiver(this);
            displayWifiList(items);
        }
    };


    private void displayWifiList(final List<String> items) {  //final String[] items) {

//        items.add("Manual add device");

        final String[] itemArray = new String[items.size()];
        items.toArray(itemArray);

        if (itemArray.length > 0) {
            Dialog d = new AlertDialog.Builder(context)
                    .setTitle("Homie device(s) found")
                    .setNegativeButton("Cancel", null)
                    .setItems(itemArray, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dlg, int position) {

                            Log.d(LOG_TAG, "network selected: #" + position + ", " + itemArray[position] + " / " + itemArray.length);
                            Intent deviceConfigurationActivity = new Intent(getApplicationContext(), DeviceAdd.class);
                            deviceConfigurationActivity.putExtra(Intent.EXTRA_TEXT, itemArray[position]);
                            //for manuall add
                            //                        if (position != itemArray.length -1) {
                            deviceConfigurationActivity.putExtra("SSID", itemArray[position]);
                            //                        }
                            startActivity(deviceConfigurationActivity);

                        }
                    })
                    .create();
            d.show();
        } else {
            Dialog d = new AlertDialog.Builder(context)
//                    .setTitle("No Homie device(s) found")
                    .setMessage("No Homie device(s) found")
                    .setPositiveButton("Ok", null)
                    .create();
            d.show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_log) {
            startActivity(new Intent(this, LogActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Method to start the service
    public void startHomieDashService() {
        Log.d(LOG_TAG, "HomieDashService should be started");
        startService(new Intent(this, HomieDashService.class));
    }

    // Method to stop the service
    public void stopMQTTService() {
        stopService(new Intent(getBaseContext(), HomieDashService.class));
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "onResume, registeritng mqtt receivers");
        IntentFilter mqttStatusFilter = new IntentFilter();
        mqttStatusFilter.addAction(HomieDashService.MQTT_STATUS_INTENT);
        registerReceiver(mqttStatusReceiver, mqttStatusFilter);

        startHomieDashService();
        mqttStatusTextView.setText(statusMsg);

        IntentFilter mqttMessageFilter = new IntentFilter();
        mqttStatusFilter.addAction(HomieDashService.MQTT_MSG_RECEIVED_INTENT);

        IntentFilter newDeviceFilter = new IntentFilter();
        newDeviceFilter.addAction(HomieDashService.MQTT_NEW_DEVICE_INTENT);
        registerReceiver(newDeviceReceiver, newDeviceFilter);

        backButtonCount = 0;

        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(LOG_TAG, "onPause");

        if (mqttStatusReceiver != null) {
            unregisterReceiver(mqttStatusReceiver);
        }

        if (newDeviceReceiver != null) {
            unregisterReceiver(newDeviceReceiver);
        }

        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "ondestroy");

        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        boolean persistentMqtt = sharedPrefs.getBoolean("mqtt_autoconnect_switch", false);

        if (!persistentMqtt) {
            Log.d(LOG_TAG, "MQTT connection not persistent, stopping service");
            stopMQTTService();
        }

        super.onDestroy();

    }

    @Override
    public void onBackPressed() {

        if (backButtonCount >= 1) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            Toast.makeText(this, getString(R.string.alert_exit), Toast.LENGTH_SHORT).show();
            backButtonCount++;
        }
    }


}
