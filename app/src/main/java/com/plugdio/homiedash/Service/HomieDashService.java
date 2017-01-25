package com.plugdio.homiedash.Service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.plugdio.homiedash.Data.CupboardSQLiteOpenHelper;
import com.plugdio.homiedash.Data.Device;
import com.plugdio.homiedash.Data.LogEntry;
import com.plugdio.homiedash.SettingsActivity;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static nl.qbusict.cupboard.CupboardFactory.cupboard;

/*
https://github.com/dirkmoors/MqttService
http://dalelane.co.uk/blog/?p=1599
*/

public class HomieDashService extends Service {

    private String LOG_TAG = "HomieDashService";
    private MqttAndroidClient client = null;

    // constants used to notify the Activity UI of received messages    
    public static final String MQTT_MSG_RECEIVED_INTENT = "com.plugdio.mqttbox.MQTTService.MSGRECVD";
    public static final String MQTT_MSG_RECEIVED_TOPIC = "com.plugdio.mqttbox.MQTTService.MSGRECVD_TOPIC";
    public static final String MQTT_MSG_RECEIVED_MSG = "com.plugdio.mqttbox.MQTTService.MSGRECVD_MSG";

    public static final String MQTT_NEW_DEVICE_INTENT = "com.plugdio.mqttbox.MQTTService.NEWDEVICE";
    public static final String MQTT_NEW_LOG_INTENT = "com.plugdio.mqttbox.MQTTService.NEWLOG";
    public static final String MQTT_LOG_TYPE = "com.plugdio.mqttbox.MQTTService.LOGTYPE";
    public static final String MQTT_LOG_DEVICE = "com.plugdio.mqttbox.MQTTService.LOGDEVICE";

    // constants used to notify the Service of messages to send   
    public static final String MQTT_PUBLISH_MSG_INTENT = "com.plugdio.mqttbox.MQTTService.SENDMSG";
    public static final String MQTT_PUBLISH_MSG_TOPIC = "com.plugdio.mqttbox.MQTTService.SENDMSG_TOPIC";
    public static final String MQTT_PUBLISH_MSG = "com.plugdio.mqttbox.MQTTService.SENDMSG_MSG";

    // constants used to tell the Activity UI the connection status
    public static final String MQTT_STATUS_INTENT = "com.plugdio.mqttbox.MQTTService.STATUS";
    public static final String MQTT_STATUS_CODE = "com.plugdio.mqttbox.MQTTService.STATUS_CODE";
    public static final String MQTT_STATUS_MSG = "com.plugdio.mqttbox.MQTTService.STATUS_MSG";

    // constant used internally to schedule the next ping event
    public static final String MQTT_PING_ACTION = "com.plugdio.mqttbox.MQTTService.PING";

    // constants used to define MQTT connection status
    public enum ConnectionStatus {
        INITIAL,                            // initial status
        NO_CONFIG,                         // missing configuration
        CONNECTING,                         // attempting to connect
        CONNECTED,                          // connected
        NOTCONNECTED_WAITINGFORINTERNET,    // can't connect because the phone
        //     does not have Internet access
        NOTCONNECTED_USERDISCONNECT,        // user has explicitly requested
        //     disconnection
        NOTCONNECTED_DATADISABLED,          // can't connect because the user
        //     has disabled data access
        NOTCONNECTED_UNKNOWNREASON          // failed to connect for some reason
    }

    // receiver that notifies the Service when the phone gets data connection
    private NetworkConnectionIntentReceiver netConnReceiver;

    // receiver that wakes the Service up when it's time to ping the server
    private PingSender pingSender;

    // status of MQTT client connection
    public ConnectionStatus connectionStatus = ConnectionStatus.NOTCONNECTED_USERDISCONNECT;
    private String baseTopic = "devices";
    private String lastTopic = null;
    private String lastMessage = null;

    private boolean persistentMqtt;
    private String clientId;

    SQLiteDatabase db;

    //  how often should the app ping the server to keep the connection alive?
    //
    //   too frequently - and you waste battery life
    //   too infrequently - and you wont notice if you lose your connection
    //                       until the next unsuccessfull attempt to ping
    //
    //   it's a trade-off between how time-sensitive the data is that your
    //      app is handling, vs the acceptable impact on battery life
    //
    //   it is perhaps also worth bearing in mind the network's support for
    //     long running, idle connections. Ideally, to keep a connection open
    //     you want to use a keep alive value that is less than the period of
    //     time after which a network operator will kill an idle connection
    private short keepAliveSeconds = 30;

    public HomieDashService() {
    }

    @Override
    public void onCreate() {

        Log.d(LOG_TAG, "HomieDashService started - onCreate");
        super.onCreate();

        CupboardSQLiteOpenHelper dbHelper = new CupboardSQLiteOpenHelper(this);
        db = dbHelper.getWritableDatabase();

        // reset status variable to initial state
        changeStatus(ConnectionStatus.NOTCONNECTED_USERDISCONNECT);

        // Let it continue running until it is stopped.

        IntentFilter prefChangeFilter = new IntentFilter();
        prefChangeFilter.addAction(SettingsActivity.PREF_CHANGED);
        registerReceiver(prefChangeReceiver, prefChangeFilter);

        persistentMqtt = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("mqtt_autoconnect_switch", false);

        clientId = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ANDROID_ID);

        log(LogEntry.LOGTYPE_LOG, null, null, "clientId: " + clientId);

        if (clientId.length() > 23) {
            clientId = clientId.substring(0, 23);
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "MQTT Service started - onStart");
        broadcastServiceStatus("MQTT Status: " + connectionStatus.toString());
        broadcastReceivedMessage(lastTopic, lastMessage);

        connect();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        log(LogEntry.LOGTYPE_LOG, null, null, "MQTT Service destroyed");
        disconnect();

        if (prefChangeReceiver != null) {
            unregisterReceiver(prefChangeReceiver);
        }
        super.onDestroy();

    }

    void connect() {

        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(this);

        String mqttHost = sharedPrefs.getString("mqtt_broker_address", null);
        String mqttPort = sharedPrefs.getString("mqtt_broker_port", null);
        Boolean mqqtAuthEnabled = sharedPrefs.getBoolean("mqtt_authentication_switch", false);
        String mqttUser = sharedPrefs.getString("mqtt_username", null);
        String mqttPass = sharedPrefs.getString("mqtt_password", null);

        if ((mqttHost == null || mqttHost.isEmpty()) || (mqttPort == null || mqttPort.isEmpty())) {
            Log.e(LOG_TAG, "Missing MQTT config");
            changeStatus(ConnectionStatus.NO_CONFIG);
            return;
        }

        if ((mqqtAuthEnabled) && (mqttUser.isEmpty() || mqttPass.isEmpty())) {
            Log.e(LOG_TAG, "Missing MQTT auth config");
            changeStatus(ConnectionStatus.NO_CONFIG);
            return;
        }

        if (isConnected()) {
            return;
        }

        if (connectionStatus == ConnectionStatus.CONNECTING) {
            return;
        }
        changeStatus(ConnectionStatus.CONNECTING);


        // changes to the phone's network - such as bouncing between WiFi
        //  and mobile data networks - can break the MQTT connection
        // the MQTT connectionLost can be a bit slow to notice, so we use
        //  Android's inbuilt notification system to be informed of
        //  network changes - so we can reconnect immediately, without
        //  haing to wait for the MQTT timeout

        if (netConnReceiver == null) {
            netConnReceiver = new NetworkConnectionIntentReceiver();
            registerReceiver(netConnReceiver,
                    new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }

        // creates the intents that are used to wake up the phone when it is
        //  time to ping the server
        if (pingSender == null) {
            pingSender = new PingSender();
            registerReceiver(pingSender, new IntentFilter(MQTT_PING_ACTION));
        }


        if (client == null) {
            client =
                    new MqttAndroidClient(this, "tcp://" + mqttHost + ":" + mqttPort,
                            clientId);

        }

        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
        if (mqqtAuthEnabled) {
            options.setUserName(mqttUser);
            options.setPassword(mqttPass.toCharArray());
        }

        try {
            IMqttToken token = client.connect(options);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected
                    log(LogEntry.LOGTYPE_LOG, null, null, "onSuccess - connected to MQTT broker");
                    try {
                        ping();
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }

                    // we need to wake up the phone's CPU frequently enough so that the
                    //  keep alive messages can be sent
                    // we schedule the first one of these now
                    scheduleNextPing();
                    subscribe();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    log(LogEntry.LOGTYPE_LOG, null, null, "onFailure: " + exception);
                    Log.e(LOG_TAG, "onFailure: " + exception);
                    changeStatus(ConnectionStatus.NOTCONNECTED_UNKNOWNREASON);

                    // if something has failed, we wait for one keep-alive period before
                    //   trying again
                    // in a real implementation, you would probably want to keep count
                    //  of how many times you attempt this, and stop trying after a
                    //  certain number, or length of time - rather than keep trying
                    //  forever.
                    // a failure is often an intermittent network issue, however, so
                    //  some limited retry is a good idea
                    scheduleNextPing();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void subscribe() {
        baseTopic = PreferenceManager.getDefaultSharedPreferences(this).getString("homie_base_topic", "devices");
        if (baseTopic.endsWith("/")) {
            baseTopic = baseTopic.substring(0, baseTopic.length() - 1);
        }
        int qos = 1;
        try {
            IMqttToken subToken = client.subscribe(baseTopic + "/#", qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

//                    log(LogEntry.LOGTYPE_LOG, null, null, "Subscribe onSuccess");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    // The subscription could not be performed, maybe the user was not
                    // authorized to subscribe on the specified topic e.g. using wildcards

                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }

        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log(LogEntry.LOGTYPE_LOG, null, null, "connectionLost: " + cause);
                if (connectionStatus != ConnectionStatus.NOTCONNECTED_USERDISCONNECT) {
                    changeStatus(ConnectionStatus.NOTCONNECTED_UNKNOWNREASON);
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(LOG_TAG, "messageArrived: " + topic + " / " + message.toString());

                processMessage(topic, message.toString());

                lastTopic = topic;
                lastMessage = message.toString();
                broadcastReceivedMessage(topic, message.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
//                log(LogEntry.LOGTYPE_LOG, null, null, "deliveryComplete");
                if (connectionStatus != ConnectionStatus.CONNECTED) {
                    changeStatus(ConnectionStatus.CONNECTED);
                }
            }
        });
    }

    public void unSubscibe() {
        final String topic = "test";
        try {
            IMqttToken unsubToken = client.unsubscribe(topic);
            unsubToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // The subscription could successfully be removed from the client
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    // some error occurred, this is very unlikely as even if the client
                    // did not had a subscription to the topic the unsubscribe action
                    // will be successfully
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            log(LogEntry.LOGTYPE_LOG, null, null, "disconnecting...");
            IMqttToken disconToken = client.disconnect();
            disconToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // we are now successfully disconnected
//                    log(LogEntry.LOGTYPE_LOG, null, null, "disconnected");

                    client.unregisterResources();
                    client.close();

                    changeStatus(ConnectionStatus.NOTCONNECTED_USERDISCONNECT);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    // something went wrong, but probably we are disconnected anyway
                }
            });

            // if we've been waiting for an Internet connection, this can be
            //  cancelled - we don't need to be told when we're connected now
            if (netConnReceiver != null) {
                unregisterReceiver(netConnReceiver);
                netConnReceiver = null;
            }

            if (pingSender != null) {
                unregisterReceiver(pingSender);
                pingSender = null;
            }

        } catch (MqttException e) {
            e.printStackTrace();
        } catch (Exception eee) {
            // probably because we hadn't registered it
//            log(LogEntry.LOGTYPE_LOG, null, null, "unregister failed: " + eee);
        }

    }

    private void changeStatus(ConnectionStatus newStatus) {
        log(LogEntry.LOGTYPE_LOG, null, null, "changeStatus -> " + newStatus.toString());

        if (connectionStatus == ConnectionStatus.CONNECTED && newStatus != ConnectionStatus.CONNECTED) {
//            Toast.makeText(this, "MQTT disconnected", Toast.LENGTH_LONG).show();
        }
        if (connectionStatus != ConnectionStatus.CONNECTED && newStatus == ConnectionStatus.CONNECTED) {
//            Toast.makeText(this, "MQTT connected", Toast.LENGTH_LONG).show();
        }

        if (newStatus == ConnectionStatus.NOTCONNECTED_UNKNOWNREASON) {
//            Toast.makeText(this, "MQTT connection failed", Toast.LENGTH_LONG).show();
        }

        connectionStatus = newStatus;
        broadcastServiceStatus("MQTT Status: " + newStatus.toString());
    }

    /************************************************************************/
    /*    METHODS - broadcasts and notifications                            */

    /************************************************************************/

    private void broadcastServiceStatus(String statusDescription) {

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MQTT_STATUS_INTENT);
        broadcastIntent.putExtra(MQTT_STATUS_CODE, connectionStatus.ordinal());
        broadcastIntent.putExtra(MQTT_STATUS_MSG, statusDescription);
        sendBroadcast(broadcastIntent);
    }

    private void broadcastReceivedMessage(String topic, String message) {

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MQTT_MSG_RECEIVED_INTENT);
        broadcastIntent.putExtra(MQTT_MSG_RECEIVED_TOPIC, topic);
        broadcastIntent.putExtra(MQTT_MSG_RECEIVED_MSG, message);
        sendBroadcast(broadcastIntent);
    }

    private BroadcastReceiver prefChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle notificationData = intent.getExtras();
            String pref = notificationData.getString("PREF");
            String value = notificationData.getString("VALUE");

            if (pref.equals("mqtt_autoconnect_switch")) {
                if (persistentMqtt && (value.equals("false"))) {
                    persistentMqtt = false;

                } else if (!persistentMqtt && (value.equals("true"))) {
                    persistentMqtt = true;
                    connect();
                }
            }

        }
    };

    /*
 * Schedule the next time that you want the phone to wake up and ping the
 *  message broker server
 */
    private void scheduleNextPing() {
        // When the phone is off, the CPU may be stopped. This means that our
        //   code may stop running.
        // When connecting to the message broker, we specify a 'keep alive'
        //   period - a period after which, if the client has not contacted
        //   the server, even if just with a ping, the connection is considered
        //   broken.
        // To make sure the CPU is woken at least once during each keep alive
        //   period, we schedule a wake up to manually ping the server
        //   thereby keeping the long-running connection open
        // Normally when using this Java MQTT client library, this ping would be
        //   handled for us.
        // Note that this may be called multiple times before the next scheduled
        //   ping has fired. This is good - the previously scheduled one will be
        //   cancelled in favour of this one.
        // This means if something else happens during the keep alive period,
        //   (e.g. we receive an MQTT message), then we start a new keep alive
        //   period, postponing the next ping.

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(MQTT_PING_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT);

        // in case it takes us a little while to do this, we try and do it
        //  shortly before the keep alive period expires
        // it means we're pinging slightly more frequently than necessary
        Calendar wakeUpTime = Calendar.getInstance();
        wakeUpTime.add(Calendar.SECOND, keepAliveSeconds);

        String TIME_FORMAT = "HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT);
        String logTimeString = sdf.format(wakeUpTime.getTime());
        log(LogEntry.LOGTYPE_LOG, null, null, "Ping scheduled to " + logTimeString);

        AlarmManager aMgr = (AlarmManager) getSystemService(ALARM_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(wakeUpTime.getTimeInMillis(), pendingIntent);
            aMgr.setAlarmClock(alarmClockInfo, pendingIntent);
        } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            aMgr.setExact(AlarmManager.RTC_WAKEUP, wakeUpTime.getTimeInMillis(), pendingIntent);
        } else {
            aMgr.set(AlarmManager.RTC_WAKEUP, wakeUpTime.getTimeInMillis(), pendingIntent);
        }


    }


    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        NetworkInfo netInfo = cm.getActiveNetworkInfo();

        return netInfo != null && netInfo.isAvailable() && netInfo.isConnected();
    }

    private boolean isConnected() {
        try {
            return ((client != null) && (client.isConnected() == true));
        } catch (NullPointerException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /*
 * Called in response to a change in network connection - after losing a
 *  connection to the server, this allows us to wait until we have a usable
 *  data connection again
 */

    private class NetworkConnectionIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            // we protect against the phone switching off while we're doing this
            //  by requesting a wake lock - we request the minimum possible wake
            //  lock - just enough to keep the CPU running until we've finished
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
            wl.acquire();

//            log(LogEntry.LOGTYPE_LOG, null, null, "NetworkConnectionIntentReceiver: isOnline()=" + isOnline() + ", isConnected()=" + isConnected());
            if (isOnline() && !isConnected()) {
                connect();
            }

            // we're finished - if the phone is switched off, it's okay for the CPU
            //  to sleep now
            wl.release();
        }
    }

    /*
     * Used to implement a keep-alive protocol at this Service level - it sends
     *  a PING message to the server, then schedules another ping after an
     *  interval defined by keepAliveSeconds
     */
    public class PingSender extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Note that we don't need a wake lock for this method (even though
            //  it's important that the phone doesn't switch off while we're
            //  doing this).
            // According to the docs, "Alarm Manager holds a CPU wake lock as
            //  long as the alarm receiver's onReceive() method is executing.
            //  This guarantees that the phone will not sleep until you have
            //  finished handling the broadcast."
            // This is good enough for our needs.

//            log(LogEntry.LOGTYPE_LOG, null, null, "PingSender onReceive. Alarm count: " + intent.getIntExtra("Intent.EXTRA_ALARM_COUNT", 0));

            if (isOnline() && !isConnected()) {
                log(LogEntry.LOGTYPE_LOG, null, null, "PingSender: isOnline()=" + isOnline() + ", isConnected()=" + isConnected());
                connect();
            } else if (!isOnline()) {
                log(LogEntry.LOGTYPE_LOG, null, null, "Waiting for network to come online again");
            } else {
                try {
//                    Log.d(LOG_TAG, "Sending keepalive");
//                    client.publish("keepalive", "1".getBytes(), 0, false);
                    ping();
                } catch (MqttException e) {
                    // if something goes wrong, it should result in connectionLost
                    //  being called, so we will handle it there
//                    log(LogEntry.LOGTYPE_LOG, null, null, "ping failed - MQTT exception: " + e);

                    // assume the client connection is broken - trash it
                    try {
                        client.disconnect();
                    } catch (MqttPersistenceException e1) {
//                        log(LogEntry.LOGTYPE_LOG, null, null, "disconnect failed - persistence exception: " + e1);
                    } catch (MqttException e2) {
//                        log(LogEntry.LOGTYPE_LOG, null, null, "disconnect failed - mqtt exception: " + e2);
                    }

                    // reconnect
                    Log.w(LOG_TAG, "PingSender onReceive: MqttException=" + e);
                    connect();
                }
            }

            // start the next keep alive period
            scheduleNextPing();
        }
    }

    private void ping() throws MqttException {
//        log(LogEntry.LOGTYPE_LOG, null, null, "Sending keepalive");
        client.publish("keepalive", "1".getBytes(), 0, false);

    }

    private void log(String type, String device, String topic, String text) {
        LogEntry l = new LogEntry(type, device, topic, text);
        long id = cupboard().withDatabase(db).put(l);
        Log.d(LOG_TAG, "adding to log: " + l.logType + " - " + l.logDevice + " - " + l.logTopic + " - " + l.logText);

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MQTT_NEW_LOG_INTENT);
        broadcastIntent.putExtra(MQTT_LOG_TYPE, type);
        broadcastIntent.putExtra(MQTT_LOG_DEVICE, device);
        broadcastIntent.putExtra(MQTT_MSG_RECEIVED_TOPIC, topic);
        broadcastIntent.putExtra(MQTT_MSG_RECEIVED_MSG, text);
        sendBroadcast(broadcastIntent);

    }

    private void processMessage(String topic, String message) {

        Log.d(LOG_TAG, "processing message: " + topic + " -> " + message);

        String myText;
        String myDeviceId;
        String myNodeId;
        String topicPattern = baseTopic + "/(.*)";

        // Create a Pattern object
        Pattern r = Pattern.compile(topicPattern);

        // Now create matcher object.
        Matcher m = r.matcher(topic);
        if (m.find()) {
            myText = m.group(1);
            String devicePattern = "([a-zA-Z0-9_.-]*)/(.*)";
            r = Pattern.compile(devicePattern);
            m = r.matcher(myText);
            if (m.find()) {
                myDeviceId = m.group(1);
                myNodeId = m.group(2);
                Log.d(LOG_TAG, "Device ID: " + myDeviceId);
                Log.d(LOG_TAG, "Node ID: " + myNodeId);

                log(LogEntry.LOGTYPE_MQTT, myDeviceId, myNodeId, message);

            } else {
                Log.d(LOG_TAG, "devicePatter didn't match: " + myText + ", this is not a homie device");
                return;
            }

        } else {
            Log.d(LOG_TAG, "topicPatter didn't match: " + topic + ", this is not a homie device");
            return;
        }

        // check if i know anything about the device
        Device myDevice = cupboard().withDatabase(db).query(com.plugdio.homiedash.Data.Device.class).withSelection("deviceId = ?", myDeviceId).get();
        if (myDevice == null) {
            Log.d(LOG_TAG, "Device doesn't exists yet, let's save it");
            myDevice = new Device(myDeviceId);
            myDevice.infoSource = Device.AUTOMATICALLY_ADDED;
            myDevice.type = Device.TYPE_HOMIE;

            long id = cupboard().withDatabase(db).put(myDevice);
            Log.d(LOG_TAG, "Device saved with id: " + id);

            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(MQTT_NEW_DEVICE_INTENT);
            broadcastIntent.putExtra(MQTT_MSG_RECEIVED_MSG, myDeviceId);
            sendBroadcast(broadcastIntent);

        } else {

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

            Log.d(LOG_TAG, "Device exists already, id: " + myDevice._id + " last seen at " + sdf.format(myDevice.lastSeenTime));

            boolean updateNeeded = false;

            ContentValues values = new ContentValues(1);
            values.put("lastSeenTime", Calendar.getInstance().getTimeInMillis());

            String nodePattern = "\\$(.*)";
            r = Pattern.compile(nodePattern);
            m = r.matcher(myNodeId);
            if (m.find()) {
                myNodeId = m.group(1);
                //this is a homie node, let's save
                if (myNodeId.equals("localip")) {
                    values.put("deviceIP", message);
                } else if (myNodeId.equals("name")) {
                    values.put("deviceName", message);
                } else if (myNodeId.equals("online")) {
                    if (!message.equals(myDevice.online)) {
                        Log.d(LOG_TAG, "Device's online state has changed from " + myDevice.online + " to " + message);
                        updateNeeded = true;
                    }
                    values.put("online", message);
                } else {
                    values.put(myNodeId, message);
                }
            }

            cupboard().withDatabase(db).update(Device.class, values, "_id = ?", myDevice._id + "");

            if (updateNeeded) {
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(MQTT_NEW_DEVICE_INTENT);
                broadcastIntent.putExtra(MQTT_MSG_RECEIVED_MSG, myDeviceId);
                sendBroadcast(broadcastIntent);
            }
        }

    }

}
