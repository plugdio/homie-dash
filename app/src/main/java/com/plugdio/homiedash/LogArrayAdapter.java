package com.plugdio.homiedash;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.plugdio.homiedash.Data.LogEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by janos on 2016-10-23.
 */

class LogArrayAdapter extends ArrayAdapter<LogEntry> {

    public String LOG_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final String LOG_TYPE_DEVICE = "DEVICELOG";
    public static final String LOG_TYPE_MAIN = "MAINLOG";

    private Context context;
    private List<LogEntry> logEntries;
    private String logViewType;
    private String deviceId;

    //constructor, call on creation
    public LogArrayAdapter(Context context, int resource) {
        super(context, resource);

        this.context = context;
    }


    //constructor, call on creation
    public LogArrayAdapter(Context context, int resource, ArrayList<LogEntry> objects, String logViewType, String deviceId) {
        super(context, resource, objects);

        this.context = context;
        this.logEntries = objects;
        this.logViewType = logViewType;
        this.deviceId = deviceId;
    }

    //called when rendering the list
    public View getView(int position, View convertView, ViewGroup parent) {

        //get the property we are displaying
        LogEntry log = logEntries.get(position);

        //get the inflater and inflate the XML layout for each item
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.list_item_log, null);

        TextView textDate = (TextView) view.findViewById(R.id.list_item_date_textview);
        TextView textLog = (TextView) view.findViewById(R.id.list_item_log_textview);

        SimpleDateFormat sdf = new SimpleDateFormat(LOG_TIME_FORMAT);
        String logTimeString = sdf.format(log.logTime);
        textDate.setText(logTimeString);

        if (this.logViewType.equals(LOG_TYPE_MAIN)) {
            if (log.logType.equals(LogEntry.LOGTYPE_LOG)) {
                textLog.setText("app | " + log.logText);
            } else {
                textLog.setText("mqtt | " + log.logDevice + ": " + log.logTopic + " - " + log.logText);
            }
        } else {
            textLog.setText(log.logTopic + " - " + log.logText);
        }
        return view;
    }
}