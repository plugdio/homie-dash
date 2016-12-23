package com.plugdio.homiedash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.plugdio.homiedash.Data.CupboardSQLiteOpenHelper;
import com.plugdio.homiedash.Data.LogEntry;
import com.plugdio.homiedash.Service.HomieDashService;

import java.util.ArrayList;

import nl.qbusict.cupboard.QueryResultIterable;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;

public class LogActivity extends AppCompatActivity {

    private final String LOG_TAG = "LogActivity";
    private SQLiteDatabase db;
    //    private ArrayAdapter<String> logAdapter;
    private ArrayAdapter<LogEntry> logAdapter;

    private boolean showMqttLogs = true;
    private boolean showAppLogs = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final ListView logListView = (ListView) findViewById(R.id.listview_logs);

        ArrayList<LogEntry> logEntries = new ArrayList<>();
        logAdapter = new LogArrayAdapter(this, 0, logEntries, LogArrayAdapter.LOG_TYPE_MAIN, null);
        logListView.setAdapter(logAdapter);

        showLogs();

    }

    private void showLogs() {

        logAdapter.clear();

        CupboardSQLiteOpenHelper dbHelper = new CupboardSQLiteOpenHelper(this);
        db = dbHelper.getWritableDatabase();

        Cursor logEntriesCursor;

        if (showMqttLogs && showAppLogs) {
            logEntriesCursor = cupboard().withDatabase(db).query(LogEntry.class).withSelection("logText is not NULL order by logTime desc").getCursor();
        } else if (showMqttLogs && !showAppLogs) {
            logEntriesCursor = cupboard().withDatabase(db).query(LogEntry.class).withSelection("logText is not NULL AND logType = '" + LogEntry.LOGTYPE_MQTT + "' order by logTime desc").getCursor();
        } else if (!showMqttLogs && showAppLogs) {
            logEntriesCursor = cupboard().withDatabase(db).query(LogEntry.class).withSelection("logText is not NULL AND logType = '" + LogEntry.LOGTYPE_LOG + "' order by logTime desc").getCursor();
        } else {
            return;
        }
        try {
            QueryResultIterable<LogEntry> itr = cupboard().withCursor(logEntriesCursor).iterate(LogEntry.class);

            for (LogEntry l : itr) {
                logAdapter.add(l);
            }
        } finally {
            // close the cursor
            logEntriesCursor.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_log, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_clear) {
            Log.d(LOG_TAG, "delete all log entries");
            cupboard().withDatabase(db).delete(LogEntry.class, null);
            finish();
            startActivity(getIntent());
            //return true;
        } else if (id == R.id.show_mqtt_logs) {
            item.setChecked(!item.isChecked());
            showMqttLogs = item.isChecked();
        } else if (id == R.id.show_app_logs) {
            item.setChecked(!item.isChecked());
            showAppLogs = item.isChecked();
        }

        showLogs();

        return super.onOptionsItemSelected(item);
    }

    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle notificationData = intent.getExtras();
            String logType = notificationData.getString(HomieDashService.MQTT_LOG_TYPE);
            String device = notificationData.getString(HomieDashService.MQTT_LOG_DEVICE);
            String topic = notificationData.getString(HomieDashService.MQTT_MSG_RECEIVED_TOPIC);
            String text = notificationData.getString(HomieDashService.MQTT_MSG_RECEIVED_MSG);

            Log.d(LOG_TAG, "log received: " + device + " / " + topic + "/" + text);
            if ((showAppLogs && logType.equals(LogEntry.LOGTYPE_LOG)) || (showMqttLogs && logType.equals(LogEntry.LOGTYPE_MQTT))) {
                logAdapter.insert(new LogEntry(logType, device, topic, text), 0);
            }


        }
    };

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "onResume, registeritng log receiver");
        IntentFilter logFilter = new IntentFilter();
        logFilter.addAction(HomieDashService.MQTT_NEW_LOG_INTENT);
        registerReceiver(logReceiver, logFilter);

        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(LOG_TAG, "onPause");

        if (logReceiver != null) {
            unregisterReceiver(logReceiver);
        }

        super.onPause();
    }


}
