package com.plugdio.homiedash.Data;

import java.util.Calendar;

/**
 * Created by janos on 2016-08-29.
 */
public class LogEntry {

    public static String LOGTYPE_LOG = "log";
    public static final String LOGTYPE_MQTT = "mqtt";

    public Long _id; // for cupboard
    public long logTime;
    public String logType; // log or mqtt
    public String logDevice;
    public String logTopic;
    public String logText;

    public LogEntry() {
        this.logTime = Calendar.getInstance().getTimeInMillis();
        this.logDevice = null;
        this.logType = null;
        this.logTopic = null;
        this.logText = null;
    }

    public LogEntry(String type, String device, String topic, String text) {
        this.logTime = Calendar.getInstance().getTimeInMillis();
        this.logType = type;
        this.logDevice = device;
        this.logTopic = topic;
        this.logText = text;
    }

}
