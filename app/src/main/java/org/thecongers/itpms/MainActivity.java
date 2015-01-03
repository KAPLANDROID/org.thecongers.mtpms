/*
Copyright (C) 2014 Keith Conger <keith.conger@gmail.com>

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.thecongers.itpms;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
 
public class MainActivity extends ActionBarActivity {

    private SharedPreferences sharedPrefs;
    private NotificationManager notificationManager;

    private View root;
    TextView txtOutput;

    private Drawable background;
    private Drawable redBackground;
    private Drawable backgroundDark;
    private Drawable redBackgroundDark;
    private Drawable txtOutBackground;
    private Drawable txtOutBackgroundDark;

    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    protected Set<BluetoothDevice>pairedDevices;

    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "iTPMS";
    final int RECEIVE_MESSAGE = 1;		// Status for Handler
    private static final int SETTINGS_RESULT = 1;
    private String address;
    private String svgFUILive;
    private String svgRUILive;
    private int frontStatus = 0;
    private int rearStatus = 0;
    private boolean itsDark = false;
    private long darkTimer = 0;
    private long lightTimer = 0;

    static SensorIdDatabase sensorDB;
    LogData logger = null;
    Handler h;

    @SuppressLint("HandlerLeak")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.activity_main);
        final ImageView  imageView = (ImageView) findViewById(R.id.imageView1);
        final ImageView  imageView2 = (ImageView) findViewById(R.id.imageView2);
        txtOutput = (TextView) findViewById(R.id.txtOutput);

        View myView = findViewById(R.id.linearLayout0);
        root = myView.getRootView();

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Watch for Bluetooth Changes
        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        IntentFilter filter3 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(btReceiver, filter1);
        this.registerReceiver(btReceiver, filter2);
        this.registerReceiver(btReceiver, filter3);

        sensorDB = new SensorIdDatabase(this);

        // Backgrounds
        background = this.getResources().getDrawable(R.drawable.rectangle_bordered);
        redBackground = this.getResources().getDrawable(R.drawable.rectangle_bordered_red);
        backgroundDark = this.getResources().getDrawable(R.drawable.rectangle_bordered_dark);
        redBackgroundDark = this.getResources().getDrawable(R.drawable.rectangle_bordered_red_dark);
        txtOutBackground = this.getResources().getDrawable(R.drawable.rectangle);
        txtOutBackgroundDark = this.getResources().getDrawable(R.drawable.rectangle_dark);

        // Draw gauges
        String pressureFormat = sharedPrefs.getString("prefpressuref", "0");
        String pressureUnit = "psi";
        if (pressureFormat.contains("1")) {
            // KPa
            pressureUnit = "KPa";
        } else if (pressureFormat.contains("2")){
            // Kg-f
            pressureUnit = "Kg-f";
        } else if (pressureFormat.contains("3")){
            // Bar
            pressureUnit = "Bar";
        }
        String temperatureUnit = "C";
        if (sharedPrefs.getString("preftempf", "0").contains("0")) {
            // F
            temperatureUnit = "F";
        }
        String svgUI = readRawTextFile(this, R.raw.ui);
        svgFUILive = svgUI.replaceAll("PP", "--");
        svgFUILive = svgFUILive.replaceAll("TTT", "---");
        svgFUILive = svgFUILive.replaceAll("VVV", "---");
        svgFUILive = svgFUILive.replaceAll("PSI", pressureUnit);
        svgFUILive = svgFUILive.replaceAll("TU", temperatureUnit);

        imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        svgRUILive = svgUI.replaceAll("PP", "--");
        svgRUILive = svgRUILive.replaceAll("TTT", "---");
        svgRUILive = svgRUILive.replaceAll("VVV", "---");
        svgRUILive = svgRUILive.replaceAll("PSI", pressureUnit);
        svgRUILive = svgRUILive.replaceAll("TU", temperatureUnit);

        imageView2.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        try
        {
            SVG svgF = SVG.getFromString(svgFUILive);
            SVG svgR = SVG.getFromString(svgRUILive);
            Drawable drawableF = new PictureDrawable(svgF.renderToPicture());
            Drawable drawableR = new PictureDrawable(svgR.renderToPicture());
            imageView.setImageDrawable(drawableF);
            imageView2.setImageDrawable(drawableR);
        }
        catch(SVGParseException e){
            Log.d(TAG, "SVG Parse Exception");
        }

        // Check if there are sensor to wheel mappings
        if (sharedPrefs.getString("prefFrontID", "").equals("") && sharedPrefs.getString("prefRearID", "").equals("")) {
            txtOutput.setText("Please map discovered sensor IDs to wheels!");
        }

        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECEIVE_MESSAGE:
                        // Message received
                        Log.d(TAG, "Serial Message Received, Length: " + msg.arg1);
                        // Check to see if message is the correct size
                        if (msg.arg1 == 13) {
                            byte[] readBuf = (byte[]) msg.obj;
                            // Convert to hex
                            String[] hexData = new String[13];
                            StringBuilder sbhex = new StringBuilder();
                            for (int i = 0; i < msg.arg1; i++) {
                                hexData[i] = String.format("%02X", readBuf[i]);
                                sbhex.append(hexData[i]);
                            }
                            // Get sensor position
                            String position = hexData[3];
                            // Get sensor ID
                            StringBuilder sensorID = new StringBuilder();
                            sensorID.append(hexData[4]);
                            sensorID.append(hexData[5]);
                            sensorID.append(hexData[6]);
                            sensorID.append(hexData[7]);

                            // Check if sensor ID is new
                            boolean checkID = sensorDB.sensorIdExists(sensorID.toString());
                            if (!checkID) {
                                // Add sensor ID to db
                                sensorDB.addID(sensorID.toString());
                                Toast.makeText(MainActivity.this,
                                        "New sensor discovered: " + sensorID.toString(),
                                        Toast.LENGTH_LONG).show();

                            }
                            // Only parse message if there is one or more sensor mappings
                            String prefFrontID = sharedPrefs.getString("prefFrontID", "");
                            String prefRearID = sharedPrefs.getString("prefRearID", "");
                            if (!prefFrontID.equals("") || !prefRearID.equals("")) {
                                if (txtOutput.getText().toString().contains("Please map")){
                                    txtOutput.setText("");
                                }
                                try {
                                    // Get temperature
                                    int tempC = Integer.parseInt(hexData[8], 16) - 50;
                                    double temp = tempC;
                                    String temperatureUnit = "C";
                                    // Get tire pressure
                                    int psi = Integer.parseInt(hexData[9], 16);
                                    double pressure = psi;
                                    String pressureUnit = "psi";
                                    // Get battery voltage
                                    double voltage = Integer.parseInt(hexData[10], 16) / 50;
                                    // Get pressure thresholds
                                    int fLowPressure = Integer.parseInt(sharedPrefs.getString("prefFrontLowPressure", "30"));
                                    int fHighPressure = Integer.parseInt(sharedPrefs.getString("prefFrontHighPressure", "46"));
                                    int rLowPressure = Integer.parseInt(sharedPrefs.getString("prefRearLowPressure", "30"));
                                    int rHighPressure = Integer.parseInt(sharedPrefs.getString("prefRearHighPressure", "46"));
                                    if (sharedPrefs.getString("preftempf", "0").contains("0")) {
                                        // F
                                        temp = (9.0 / 5.0) * tempC + 32.0;
                                        temperatureUnit = "F";
                                    }
                                    int formattedTemperature = (int) (temp + 0.5d);
                                    String pressureFormat = sharedPrefs.getString("prefpressuref", "0");
                                    if (pressureFormat.contains("1")) {
                                        // KPa
                                        pressure = psi * 6.894757293168361;
                                        pressureUnit = "KPa";
                                    } else if (pressureFormat.contains("2")) {
                                        // Kg-f
                                        pressure = psi * 0.070306957965539;
                                        pressureUnit = "Kg-f";
                                    } else if (pressureFormat.contains("3")) {
                                        // Bar
                                        pressure = psi * 0.0689475729;
                                        pressureUnit = "Bar";
                                    }
                                    int formattedPressure = (int) (pressure + 0.5d);
                                    // Get checksum
                                    String checksum = hexData[11];
                                    Log.d(TAG, "Sensor ID: " + sensorID.toString() + ", Sensor Position: " + position + ", Temperature(" + temperatureUnit + "): " + String.valueOf(temp) + ", Pressure(" + pressureUnit + "): " + String.valueOf(pressure) + ", Voltage: " + String.valueOf(voltage) + ", Checksum: " + checksum + ", Data: " + sbhex.toString() + ", Bytes:" + msg.arg1);

                                    if (sensorID.toString().equals(prefFrontID)) {
                                        Log.d(TAG, "Front ID matched");
                                        // Check for data logging enabled
                                        if (sharedPrefs.getBoolean("prefDataLogging", false)) {
                                            // Log data
                                            if (logger == null) {
                                                logger = new LogData();
                                            }
                                            logger.write("rear", String.valueOf(psi), String.valueOf(temp), String.valueOf(voltage));
                                        }
                                        final ImageView  imageView = (ImageView) findViewById(R.id.imageView1);
                                        int notificationID = 0;
                                        if (psi <= fLowPressure) {
                                            frontStatus = 1;
                                            // Send notification
                                            Notify("iTPMS", "Low front tire pressure!", notificationID);
                                            // Set background to red
                                            if (itsDark){
                                                imageView.setBackground(redBackgroundDark);
                                            } else {
                                                imageView.setBackground(redBackground);
                                            }
                                        } else if (psi >= fHighPressure) {
                                            frontStatus = 2;
                                            // Send notification
                                            Notify("iTPMS", "High front tire pressure!", notificationID);
                                            // Set background to red
                                            if (itsDark){
                                                imageView.setBackground(redBackgroundDark);
                                            } else {
                                                imageView.setBackground(redBackground);
                                            }
                                        } else {
                                            frontStatus = 0;
                                            if (notificationManager != null) {
                                                notificationManager.cancel(notificationID);
                                            }
                                            // Reset background
                                            if (itsDark){
                                                imageView.setBackground(backgroundDark);
                                            } else {
                                                imageView.setBackground(background);
                                            }
                                        }
                                        String svgFUI = readRawTextFile(MainActivity.this, R.raw.ui);
                                        svgFUILive = svgFUI.replaceAll("PP", String.valueOf(formattedPressure));
                                        svgFUILive = svgFUILive.replaceAll("TTT", String.valueOf(formattedTemperature));
                                        svgFUILive = svgFUILive.replaceAll("VVV", String.format("%.2f", voltage));
                                        svgFUILive = svgFUILive.replaceAll("PSI", pressureUnit);
                                        svgFUILive = svgFUILive.replaceAll("TU", temperatureUnit);
                                        if (itsDark){
                                            svgFUILive = svgFUILive.replaceAll("#000000", "#FFFFFF");
                                        }
                                        try {
                                            SVG svg = SVG.getFromString(svgFUILive);
                                            Drawable drawable = new PictureDrawable(svg.renderToPicture());
                                            imageView.setImageDrawable(drawable);
                                        } catch (SVGParseException e) {
                                            Log.d(TAG, "SVG Parse Exception");
                                        }
                                        imageView.invalidate();

                                    } else if (sensorID.toString().equals(prefRearID)) {
                                        Log.d(TAG, "Rear ID matched");
                                        // Check for data logging enabled
                                        if (sharedPrefs.getBoolean("prefDataLogging", false)) {
                                            // Log data
                                            if (logger == null) {
                                                logger = new LogData();
                                            }
                                            logger.write("rear", String.valueOf(psi), String.valueOf(temp), String.valueOf(voltage));
                                        }
                                        final ImageView  imageView2 = (ImageView) findViewById(R.id.imageView2);
                                        int notificationID = 1;
                                        if (psi <= rLowPressure) {
                                            rearStatus = 1;
                                            // Send notification
                                            Notify("iTPMS", "Low rear tire pressure!", notificationID);
                                            // Set background to red
                                            if (itsDark){
                                                imageView2.setBackground(redBackgroundDark);
                                            } else {
                                                imageView2.setBackground(redBackground);
                                            }
                                        } else if (psi >= rHighPressure) {
                                            rearStatus = 2;
                                            // Send notification
                                            Notify("iTPMS", "High rear tire pressure!", notificationID);
                                            // Set background to red
                                            if (itsDark){
                                                imageView2.setBackground(redBackgroundDark);
                                            } else {
                                                imageView2.setBackground(redBackground);
                                            }

                                        } else {
                                            rearStatus = 0;
                                            if (notificationManager != null) {
                                                notificationManager.cancel(notificationID);
                                            }
                                            // Reset background
                                            if (itsDark){
                                                imageView2.setBackground(backgroundDark);
                                            } else {
                                                imageView2.setBackground(background);
                                            }
                                        }
                                        String svgRUI = readRawTextFile(MainActivity.this, R.raw.ui);
                                        svgRUILive = svgRUI.replaceAll("PP", String.valueOf(formattedPressure));
                                        svgRUILive = svgRUILive.replaceAll("TTT", String.valueOf(formattedTemperature));
                                        svgRUILive = svgRUILive.replaceAll("VVV", String.format("%.2f", voltage));
                                        svgRUILive = svgRUILive.replaceAll("PSI", pressureUnit);
                                        svgRUILive = svgRUILive.replaceAll("TU", temperatureUnit);
                                        if (itsDark){
                                            svgRUILive = svgRUILive.replaceAll("#000000", "#FFFFFF");
                                        }
                                        try {
                                            SVG svg = SVG.getFromString(svgRUILive);
                                            Drawable drawable = new PictureDrawable(svg.renderToPicture());
                                            imageView2.setImageDrawable(drawable);
                                        } catch (SVGParseException e) {
                                            Log.d(TAG, "SVG Parse Exception");
                                        }
                                        imageView2.invalidate();
                                    }
                                    // Update txtOutput box
                                    if ((frontStatus == 0) && (rearStatus == 0)){
                                        txtOutput.setText("");
                                    } else if ((frontStatus == 1) && (rearStatus == 0)){
                                        txtOutput.setText("Low front tire pressure!");
                                    } else if ((frontStatus == 2) && (rearStatus == 0)){
                                        txtOutput.setText("High front tire pressure!");
                                    } else if ((rearStatus == 1) && (frontStatus == 0)){
                                        txtOutput.setText("Low rear tire pressure!");
                                    } else if ((rearStatus == 2) && (frontStatus == 0)){
                                        txtOutput.setText("High rear tire pressure!");
                                    } else if ((frontStatus == 1) && (rearStatus == 1)){
                                        txtOutput.setText("Low front and rear tire pressure!");
                                    } else if ((frontStatus == 2) && (rearStatus == 2)){
                                        txtOutput.setText("High front and rear tire pressure!");
                                    } else if ((frontStatus == 1) && (rearStatus == 2)){
                                        txtOutput.setText("Low front and High rear tire pressure!");
                                    } else if ((frontStatus == 2) && (rearStatus == 1)){
                                        txtOutput.setText("High front and Low rear tire pressure!");
                                    }
                                    if (!itsDark) {
                                        txtOutput.setBackground(txtOutBackground);
                                        txtOutput.setTextColor(getResources().getColor(android.R.color.white));
                                    } else {
                                        txtOutput.setBackground(txtOutBackgroundDark);
                                        txtOutput.setTextColor(getResources().getColor(android.R.color.black));
                                    }
                                } catch (NumberFormatException e) {
                                    Log.d(TAG, "Malformed message, unexpected value");
                                }
                            }
                        } else {
                            Log.d(TAG, "Malformed message, message length: " + msg.arg1);
                        }
                        break;
                }
            }
        };
        // Light Sensor Stuff
        SensorManager sensorManager
                = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor
                = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null){
            Log.d(TAG,"Light sensor not found");
        }else {
            float max = lightSensor.getMaximumRange();
            sensorManager.registerListener(lightSensorEventListener,
                    lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG,"Light Sensor Max Value: " + max);
        }
        // Try to connect to iTPMSystem
        btConnect();
    }

    // Called when screen rotates or size changes
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_main);

        // Redraw Screen
        final ImageView  imageView = (ImageView) findViewById(R.id.imageView1);
        final ImageView  imageView2 = (ImageView) findViewById(R.id.imageView2);
        // Gauges
        if (itsDark){
            svgFUILive = svgFUILive.replaceAll("#000000", "#FFFFFF");
            svgRUILive = svgRUILive.replaceAll("#000000", "#FFFFFF");
        }
        try
        {
            SVG svgF = SVG.getFromString(svgFUILive);
            SVG svgR = SVG.getFromString(svgRUILive);
            Drawable drawableF = new PictureDrawable(svgF.renderToPicture());
            Drawable drawableR = new PictureDrawable(svgR.renderToPicture());
            imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            imageView2.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            imageView.setImageDrawable(drawableF);
            imageView2.setImageDrawable(drawableR);
        }
        catch(SVGParseException e){
            Log.d(TAG, "SVG Parse Exception");
        }
        // Text
        CharSequence currentTxt = txtOutput.getText();
        txtOutput= (TextView) findViewById(R.id.txtOutput);
        txtOutput.setText(currentTxt);
        // Background
        if (!itsDark){
            txtOutput.setBackground(txtOutBackground);
            txtOutput.setTextColor(getResources().getColor(android.R.color.white));
            if (frontStatus > 0) {
                imageView.setBackground(redBackground);
            } else {
                imageView.setBackground(background);
            }
            if (rearStatus > 0) {
                imageView2.setBackground(redBackground);
            } else {
                imageView2.setBackground(background);
            }

        } else {
            txtOutput.setBackground(txtOutBackgroundDark);
            txtOutput.setTextColor(getResources().getColor(android.R.color.black));
            if (frontStatus > 0) {
                imageView.setBackground(redBackgroundDark);
            } else {
                imageView.setBackground(backgroundDark);
            }
            if (rearStatus > 0) {
                imageView2.setBackground(redBackgroundDark);
            } else {
                imageView2.setBackground(backgroundDark);
            }
        }
    }

    //Draw options menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    // When options menu item is selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // Settings Menu was selected
                Intent i = new Intent(getApplicationContext(), UserSettingActivity.class);
                startActivityForResult(i, SETTINGS_RESULT);
                return true;
            case R.id.action_sensorReset:
                // Sensor Reset Menu Item was selected
                sensorDB.purgeID();
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                settings.edit().remove("prefFrontID").apply();
                settings.edit().remove("prefRearID").apply();
                return true;
            case R.id.action_exit:
                // Exit menu item was selected
                if (logger != null){
                    logger.shutdown();
                }
                finish();
                System.exit(0);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //Runs when settings are updated
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==SETTINGS_RESULT)
        {
            updateUserSettings();
        }
    }

    // Update UI when settings are updated
    private void updateUserSettings()
    {
        // Shutdown Logger
        if (!sharedPrefs.getBoolean("prefDataLogging", false) && (logger != null)) {
            logger.shutdown();
        }

        String prefFrontID = sharedPrefs.getString("prefFrontID", "");
        String prefRearID = sharedPrefs.getString("prefRearID", "");
        if (!prefFrontID.equals("") || !prefRearID.equals("")) {
            if (txtOutput.getText().toString().contains("Please map")) {
                txtOutput.setText("");
            }
        }
    }

    // Connect to iTPMSystem
    private boolean btConnect() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();
        if(btAdapter!=null) {
            pairedDevices = btAdapter.getBondedDevices();
            // If there are paired devices
            if (pairedDevices.size() > 0) {
                // Loop through paired devices
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().contains("iTPMS")) {
                        address = device.getAddress();
                        Log.d(TAG, "Paired iTPMSystem found: " + device.getName() + " " + device.getAddress());
                    }
                }
                if (address == null) {
                    Toast.makeText(MainActivity.this,
                            "No previously paired iTPMSystem found.",
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            if (address != null){
                // Set up a pointer to the remote node using it's address.
                BluetoothDevice device = btAdapter.getRemoteDevice(address);

                // Two things are needed to make a connection:
                //   A MAC address, which we got above.
                //   A Service ID or UUID.  In this case we are using the
                //     UUID for SPP.

                try {
                    btSocket = createBluetoothSocket(device);
                } catch (IOException e) {
                    Log.d(TAG,"Bluetooth socket create failed: " + e.getMessage() + ".");
                    return false;
                }

                // Discovery is resource intensive.  Make sure it isn't going on
                // when you attempt to connect and pass your message.
                btAdapter.cancelDiscovery();

                // Establish the connection.  This will block until it connects.
                Log.d(TAG, "Connecting to the iTPMSystem...");
                try {
                    btSocket.connect();
                    Log.d(TAG, "Connected to: " + device.getName() + " " + device.getAddress());
                    Toast.makeText(MainActivity.this,
                            "Connected to: " + device.getName() + " " + device.getAddress(),
                            Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    try {
                        btSocket.close();
                        return false;
                    } catch (IOException e2) {
                        Log.d(TAG,"Unable to close socket during connection failure");
                        return false;
                    }
                }

                ConnectedThread mConnectedThread = new ConnectedThread(btSocket);
                mConnectedThread.start();
            } else {
                Toast.makeText(MainActivity.this,
                        "No previously paired iTPMSystem found.",
                        Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
        Log.d(TAG, "Bluetooth not supported");
        return false;
    }

    // Close Bluetooth Socket
    private void btReset() {
        if (btSocket != null) {
            try {
                btSocket.close();
            } catch (Exception e) {
                Log.d(TAG,"Unable to close socket during connection failure");
            }
            btSocket = null;
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    // Listens for Bluetooth broadcasts
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if ((BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) && (device.getName().contains("iTPMS"))) {
                // Do something if connected
                Log.d(TAG, "iTPMSystem Connected");
                btConnect();
            }
            else if ((BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) && (device.getName().contains("iTPMS"))) {
                // Do something if disconnected
                Log.d(TAG, "iTPMSystem Disconnected");
            }
        }
    };

    // Check current Bluetooth state
    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        if(btAdapter==null) {
            Log.d(TAG, "Bluetooth not supported");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "Bluetooth is on");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpInput = null;

            // Get the input stream, using temp objects because
            // member streams are final
            try {
                tmpInput = socket.getInputStream();
            } catch (IOException e) {
                Log.d(TAG, "IO Exception getting input stream");
            }
            mmInStream = tmpInput;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);		// Get number of bytes and message in "buffer"
                    h.obtainMessage(RECEIVE_MESSAGE, bytes, -1, buffer).sendToTarget();		// Send to message queue Handler
                } catch (IOException e) {
                    Log.d(TAG, "IO Exception while reading stream");
                    btReset();
                    break;
                }
            }
        }
    }

    //Read raw text file
    public static String readRawTextFile(Context ctx, int resId)
    {
        InputStream inputStream = ctx.getResources().openRawResource(resId);

        InputStreamReader inputreader = new InputStreamReader(inputStream);
        BufferedReader buffreader = new BufferedReader(inputreader);
        String line;
        StringBuilder text = new StringBuilder();

        try {
            while (( line = buffreader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return text.toString();
    }

    //Send Notification
    private void Notify(String notificationTitle, String notificationMessage, int notificationID)
    {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, 0);

        String alertURI = sharedPrefs.getString("prefsound","content://settings/system/notification_sound");
        Uri soundURI = Uri.parse(alertURI);
        // Build notification
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle(notificationTitle)
                .setContentText(notificationMessage)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(pendingIntent);
        // Check for LED enabled
        if (sharedPrefs.getBoolean("prefNotificationLED", true)) {
            builder.setLights(android.graphics.Color.RED, 1500, 1500);
        }
        // Check for sound enabled
        if (sharedPrefs.getBoolean("prefNotificationSound", true)) {
            builder.setSound(soundURI);
        }
        Notification notification = builder.build();
        // Check for vibration enabled
        if (sharedPrefs.getBoolean("prefNotificationVibrate", true)) {
            notification.defaults|= Notification.DEFAULT_VIBRATE;
        }
        // Make alert repeat until read
        notification.flags |= Notification.FLAG_INSISTENT;
        // Hide notification after its been selected
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.priority = Notification.PRIORITY_MAX;
        // Send notification
        notificationManager.notify(notificationID, notification);
    }

    // Listens for light sensor events
    SensorEventListener lightSensorEventListener
            = new SensorEventListener(){

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do something

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (sharedPrefs.getBoolean("prefAutoNightMode", false)) {
                int delay = (Integer.parseInt(sharedPrefs.getString("prefAutoNightModeDelay", "30")) * 1000);
                if(event.sensor.getType()==Sensor.TYPE_LIGHT){
                    float currentReading = event.values[0];
                    // TODO: Proper light value
                    if (currentReading < 20.0){
                        lightTimer = 0;
                        if (darkTimer == 0){
                            darkTimer = System.currentTimeMillis();
                        } else {
                            long currentTime = System.currentTimeMillis();
                            long duration = (currentTime - darkTimer);
                            if ((duration >= delay) && (!itsDark)){ // divide be 1000 for secs
                                itsDark = true;
                                Log.d(TAG,"Its night");
                                // Redraw Screen
                                final ImageView  imageView = (ImageView) findViewById(R.id.imageView1);
                                final ImageView  imageView2 = (ImageView) findViewById(R.id.imageView2);
                                root.setBackgroundColor(getResources().getColor(android.R.color.background_dark));
                                txtOutput.setBackground(txtOutBackgroundDark);
                                if (frontStatus > 0) {
                                    imageView.setBackground(redBackgroundDark);
                                } else {
                                    imageView.setBackground(backgroundDark);
                                }
                                if (rearStatus > 0) {
                                    imageView2.setBackground(redBackgroundDark);
                                } else {
                                    imageView2.setBackground(backgroundDark);
                                }
                                svgFUILive = svgFUILive.replaceAll("#000000","#FFFFFF");
                                svgRUILive = svgRUILive.replaceAll("#000000","#FFFFFF");
                                try
                                {
                                    SVG svgF = SVG.getFromString(svgFUILive);
                                    SVG svgR = SVG.getFromString(svgRUILive);
                                    Drawable drawableF = new PictureDrawable(svgF.renderToPicture());
                                    Drawable drawableR = new PictureDrawable(svgR.renderToPicture());
                                    imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                                    imageView2.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                                    imageView.setImageDrawable(drawableF);
                                    imageView2.setImageDrawable(drawableR);
                                }
                                catch(SVGParseException e){
                                    Log.d(TAG, "SVG Parse Exception");
                                }
                            }
                        }
                    } else {
                        darkTimer = 0;
                        if (lightTimer == 0){
                            lightTimer = System.currentTimeMillis();
                        } else {
                            long currentTime = System.currentTimeMillis();
                            long duration = (currentTime - lightTimer);
                            if ((duration >= delay) && (itsDark)){
                                itsDark = false;
                                Log.d(TAG,"Its day");
                                // Redraw Screen
                                final ImageView  imageView = (ImageView) findViewById(R.id.imageView1);
                                final ImageView  imageView2 = (ImageView) findViewById(R.id.imageView2);
                                root.setBackgroundColor(getResources().getColor(android.R.color.white));
                                txtOutput.setBackground(txtOutBackground);
                                if (frontStatus > 0) {
                                    imageView.setBackground(redBackground);
                                } else {
                                    imageView.setBackground(background);
                                }
                                if (rearStatus > 0) {
                                    imageView2.setBackground(redBackground);
                                } else {
                                    imageView2.setBackground(background);
                                }
                                svgFUILive = svgFUILive.replaceAll("#FFFFFF","#000000");
                                svgRUILive = svgRUILive.replaceAll("#FFFFFF","#000000");
                                try
                                {
                                    SVG svgF = SVG.getFromString(svgFUILive);
                                    SVG svgR = SVG.getFromString(svgRUILive);
                                    Drawable drawableF = new PictureDrawable(svgF.renderToPicture());
                                    Drawable drawableR = new PictureDrawable(svgR.renderToPicture());
                                    imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                                    imageView2.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                                    imageView.setImageDrawable(drawableF);
                                    imageView2.setImageDrawable(drawableR);
                                }
                                catch(SVGParseException e){
                                    Log.d(TAG, "SVG Parse Exception");
                                }

                            }
                        }
                    }
                }

            }
        }

    };
}