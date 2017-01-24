package com.plugdio.homiedash;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import com.plugdio.homiedash.Data.Device;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by janos on 2016-10-23.
 */

class DeviceArrayAdapter extends ArrayAdapter<Device> {

    private Context context;
    private List<Device> deviceEntries;
    private String LOG_TAG = "DeviceArrayAdapter";

    //constructor, call on creation
    public DeviceArrayAdapter(Context context, int resource) {
        super(context, resource);

        this.context = context;
        this.deviceEntries = new ArrayList<Device>();
    }


    //constructor, call on creation
    public DeviceArrayAdapter(Context context, int resource, ArrayList<Device> objects) {
        super(context, resource, objects);

        this.context = context;
        this.deviceEntries = objects;
    }

    //called when rendering the list
    public View getView(int position, View convertView, ViewGroup parent) {

        if ((deviceEntries.size() == 0) || (deviceEntries.size() < position)) {
            return null;
        }

        //get the property we are displaying
        Device device = deviceEntries.get(position);

        //get the inflater and inflate the XML layout for each item
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.list_item_device, null);

        TextView textDeviceNamme = (TextView) view.findViewById(R.id.list_item_device_textview);
        RadioButton radioDeviceStatus = (RadioButton) view.findViewById(R.id.device_status);


        if (device.deviceName == null || device.deviceName.equals(null)) {
            textDeviceNamme.setText(" - (" + device.deviceId + ")");
        } else {
            textDeviceNamme.setText(device.deviceName + " (" + device.deviceId + ")");
        }


        if (device.online != null && device.online.equals("true")) {
            radioDeviceStatus.setChecked(true);
        } else {
            radioDeviceStatus.setChecked(false);
        }

        return view;
    }
}