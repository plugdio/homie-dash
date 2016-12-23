package com.plugdio.homiedash;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle("About");

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            int versionCode = pInfo.versionCode;

            TextView versionTextView = (TextView) findViewById(R.id.about_version);
            versionTextView.setText("Version: " + versionName + " (" + versionCode + ")");

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

    }
}
