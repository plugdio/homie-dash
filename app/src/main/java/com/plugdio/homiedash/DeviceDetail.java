package com.plugdio.homiedash;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.plugdio.homiedash.Data.CupboardSQLiteOpenHelper;
import com.plugdio.homiedash.Data.Device;
import com.plugdio.homiedash.Data.LogEntry;
import com.plugdio.homiedash.Service.HomieDashService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import nl.qbusict.cupboard.QueryResultIterable;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;

public class DeviceDetail extends AppCompatActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */


    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    private final String LOG_TAG = DeviceDetail.class.getName();

    private String deviceId;
    private String deviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = this.getIntent();
        if ((intent != null) && (intent.hasExtra(Intent.EXTRA_TEXT))) {
            deviceId = intent.getStringExtra(Intent.EXTRA_TEXT);
            deviceName = intent.getStringExtra(Intent.EXTRA_TITLE);
        } else {
            Log.e(LOG_TAG, "no intent");
        }


        setContentView(R.layout.activity_device_detail);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle(deviceName + " (" + deviceId + ")");
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_device_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.delete_device) {
            Log.d(LOG_TAG, "Let's delete device with id: " + deviceId);
            CupboardSQLiteOpenHelper dbHelper = new CupboardSQLiteOpenHelper(getApplicationContext());
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            cupboard().withDatabase(db).delete(Device.class, "deviceId = ?", deviceId);
            startActivity(new Intent(this, MainActivity.class));
        } else if (id == R.id.delete_device_logs) {
            Log.d(LOG_TAG, "Let's delete device logs with id: " + deviceId);
            CupboardSQLiteOpenHelper dbHelper = new CupboardSQLiteOpenHelper(getApplicationContext());
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            cupboard().withDatabase(db).delete(LogEntry.class, "logDevice = ?", deviceId);
            ((PlaceholderFragment) mSectionsPagerAdapter.myFragement).clearLog();
        }

        return super.onOptionsItemSelected(item);
    }

    public static class PlaceholderFragment extends Fragment {

        private static final String ARG_SECTION_NUMBER = "section_number";

        private final String LOG_TAG = "DeviceTab";
        private SQLiteDatabase db;
        ArrayAdapter<LogEntry> logAdapter;
        private String LOG_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            Log.d("DeviceTab", "fragment id: " + fragment.getId());
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView;

            String deviceId = null;

            Intent intent = getActivity().getIntent();
            if ((intent != null) && (intent.hasExtra(Intent.EXTRA_TEXT))) {
                deviceId = intent.getStringExtra(Intent.EXTRA_TEXT);
            } else {
                Log.e(LOG_TAG, "no intent");
            }

            CupboardSQLiteOpenHelper dbHelper = new CupboardSQLiteOpenHelper(getActivity().getApplicationContext());
            db = dbHelper.getWritableDatabase();

            if (getArguments().getInt(ARG_SECTION_NUMBER) == 1) {

                rootView = inflater.inflate(R.layout.fragment_device_detail_device, container, false);

                TextView textDeviceId = (TextView) rootView.findViewById(R.id.device_id);
                final TextView textDeviceDescription = (TextView) rootView.findViewById(R.id.device_description);
                TextView textDeviceFriendlyName = (TextView) rootView.findViewById(R.id.device_friendlyname);
                TextView textDeviceOnline = (TextView) rootView.findViewById(R.id.device_online);
                TextView textDeviceLastSeen = (TextView) rootView.findViewById(R.id.device_last_seen);
                TextView textDeviceUptime = (TextView) rootView.findViewById(R.id.device_uptime);
                TextView textDeviceSignal = (TextView) rootView.findViewById(R.id.device_signal);
                TextView textDeviceIP = (TextView) rootView.findViewById(R.id.device_ip_address);
                TextView textDeviceFirmware = (TextView) rootView.findViewById(R.id.device_firmware);
                Button buttonEditDeviceDescription = (Button) rootView.findViewById(R.id.edit_description);

                final Device myDevice = cupboard().withDatabase(db).query(com.plugdio.homiedash.Data.Device.class).withSelection("deviceId = ?", deviceId).get();
                if (myDevice == null) {
                    Log.e(LOG_TAG, "Device doesn't exists");
                } else {

                    SimpleDateFormat sdf = new SimpleDateFormat(LOG_TIME_FORMAT);

                    Log.d(LOG_TAG, "Device exists already, id: " + myDevice._id + " last seen at " + sdf.format(myDevice.lastSeenTime));

                    textDeviceId.setText(myDevice.deviceId);
                    textDeviceOnline.setText(myDevice.online);

                    if (myDevice.description != null) {
                        textDeviceDescription.setText(myDevice.description);
                    } else {
                        textDeviceDescription.setText("-");
                    }

                    buttonEditDeviceDescription.setClickable(true);
                    buttonEditDeviceDescription.setEnabled(true);
                    buttonEditDeviceDescription.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Log.d(LOG_TAG, "edit desc");

                            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                            builder.setTitle("Device description");

                            final EditText input = new EditText(getContext());

                            input.setText(textDeviceDescription.getText());
                            builder.setView(input);

                            builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String desc = input.getText().toString();
                                    Log.d(LOG_TAG, "input: " + desc);
//                                    ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);

                                    ContentValues values = new ContentValues(1);
                                    values.put("description", desc);

                                    cupboard().withDatabase(db).update(Device.class, values, "_id = ?", myDevice._id + "");
                                    textDeviceDescription.setText(desc);

                                }
                            });
                            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //dialog.cancel();
//                                    ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
                                    dialog.dismiss();
                                }
                            });
                            builder.show();

                        }
                    });

                    if (myDevice.deviceName != null) {
                        textDeviceFriendlyName.setText(myDevice.deviceName);
                    } else {
                        textDeviceFriendlyName.setText("-");
                    }

                    textDeviceFirmware.setText(myDevice.fwName + " " + myDevice.fwVersion);

                    if ((myDevice.online != null) && (myDevice.online.equals("true"))) {
                        textDeviceLastSeen.setText(sdf.format(myDevice.lastSeenTime));

                        long dayCount = TimeUnit.SECONDS.toDays(myDevice.uptime);
                        long secondsCount = myDevice.uptime - TimeUnit.DAYS.toSeconds(dayCount);
                        long hourCount = TimeUnit.SECONDS.toHours(secondsCount);
                        secondsCount -= TimeUnit.HOURS.toSeconds(hourCount);
                        long minutesCount = TimeUnit.SECONDS.toMinutes(secondsCount);
                        secondsCount -= TimeUnit.MINUTES.toSeconds(minutesCount);

                        String upTimeString = dayCount + " d " + hourCount + ":" + minutesCount + ":" + secondsCount;

                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("%d %s, ", dayCount, (dayCount < 2) ? "day"
                                : "days"));
                        sb.append(String.format("%02d:%02d:%02d %s", hourCount, minutesCount,
                                secondsCount, (hourCount == 1) ? "" : ""));

                        textDeviceUptime.setText(sb.toString());

                        textDeviceSignal.setText(myDevice.signal + "");
                        if (myDevice.deviceIP != null) {
                            textDeviceIP.setText(myDevice.deviceIP);
                        } else {
                            textDeviceIP.setText("n.a.");
                        }
                    } else {
                        textDeviceOnline.setText("false");
                        textDeviceLastSeen.setText("n.a.");
                        textDeviceUptime.setText("n.a.");
                        textDeviceSignal.setText("n.a.");
                        textDeviceIP.setText("n.a.");
                    }

                }

            } else {

                rootView = inflater.inflate(R.layout.fragment_device_detail_log, container, false);

                ListView logListView = (ListView) rootView.findViewById(R.id.listview_logs);

                ArrayList<LogEntry> logEntries = new ArrayList<>();
                logAdapter = new LogArrayAdapter(this.getContext(), 0, logEntries, LogArrayAdapter.LOG_TYPE_DEVICE, deviceId);

                logListView.setAdapter(logAdapter);

                Cursor logEntriesCursor = cupboard().withDatabase(db).query(LogEntry.class).withSelection("logType = ? AND logDevice = '" + deviceId + "' order by logTime desc", LogEntry.LOGTYPE_MQTT).getCursor();
                try {

                    QueryResultIterable<LogEntry> itr = cupboard().withCursor(logEntriesCursor).iterate(LogEntry.class);

                    for (LogEntry l : itr) {
                        logAdapter.add(l);
                    }
                } finally {
                    logEntriesCursor.close();
                }

            }
            return rootView;
        }


        public void updateLog(String deviceId, String topic, String text) {
            if (logAdapter != null) {
                logAdapter.insert(new LogEntry(LogEntry.LOGTYPE_MQTT, deviceId, topic, text), 0);
            }

        }

        public void clearLog() {
            if (logAdapter != null) {
                logAdapter.clear();
            }
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        public Fragment myFragement;

        @Override
        public Fragment getItem(int position) {
            myFragement = PlaceholderFragment.newInstance(position + 1);
            return myFragement;
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Details";
                case 1:
                    return "MQTT logs";
            }
            return null;
        }
    }


    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle notificationData = intent.getExtras();
            String logType = notificationData.getString(HomieDashService.MQTT_LOG_TYPE);
            String device = notificationData.getString(HomieDashService.MQTT_LOG_DEVICE);
            String topic = notificationData.getString(HomieDashService.MQTT_MSG_RECEIVED_TOPIC);
            String text = notificationData.getString(HomieDashService.MQTT_MSG_RECEIVED_MSG);

            Log.d(LOG_TAG, "log received: (" + logType + ") " + device + " - " + topic + "/" + text + " : " + deviceId);
            if (LogEntry.LOGTYPE_MQTT.equals(logType) && deviceId.equals(device)) {
                Log.d(LOG_TAG, "log received: " + device + " - " + topic + "/" + text);
                ((PlaceholderFragment) mSectionsPagerAdapter.myFragement).updateLog(deviceId, topic, text);
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
