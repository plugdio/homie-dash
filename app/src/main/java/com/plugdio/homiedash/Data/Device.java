package com.plugdio.homiedash.Data;

import java.util.Calendar;

/**
 * Created by janos on 2016-09-05.
 */
public class Device {

    public static final String MANUALLY_ADDED = "manual";
    public static final String AUTOMATICALLY_ADDED = "automatic";
    public static final String TYPE_GENERIC = "generic";
    public static final String TYPE_HOMIE = "homie";

    public Long _id; // for cupboard
    public String deviceId;
    public String deviceName;
    public String online;
    public String deviceIP;
    public String signal;
    public String fwName;
    public String fwVersion;
    public String nodes;
    public int uptime;
    public long lastSeenTime;
    public long addedTime;
    public String infoSource;
    public String description;
    public String type;

    public Device() {
        this.addedTime = Calendar.getInstance().getTimeInMillis();
        this.deviceId = null;
    }

    public Device(String deviceId) {
        this.addedTime = Calendar.getInstance().getTimeInMillis();
        this.deviceId = deviceId;
    }

}
