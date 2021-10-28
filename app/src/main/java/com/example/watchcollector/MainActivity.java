/*Wear OS application that collects accelerometer, gyroscope and magnetometer data from the inertial measurement unit from a smartwatch and sends it to a server via WebSocket*/
package com.example.watchcollector;

import android.graphics.Color;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.example.watchcollector.bodySide;

import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;

public class MainActivity extends WearableActivity implements SensorEventListener {

    private boolean startWatchRecording = false;
    private boolean recording = false;
    private bodySide side = bodySide.NON_DEFINED;
    private String sideString = "";

    //Websocket
    private Socket mSocket;

    //GUI
    private TextView header;
    //private TextView currentWatchDataX, currentWatchDataY, currentWatchDataZ;
    private Button leftButton;
    private Button rightButton;

    //smartwatch sensor
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;

    //sensor data
    float[] gyroValues;
    float[] values;
    float[] magneticValues;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeViews();

        // init sensors
        // accelerometer
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // watch accelerometer exists
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            System.out.println("No watch accelerometer"); // no watch accelerometer available
        }
        // gyroscope
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            // phone gyroscope exists
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            System.out.println("No watch gyroscope"); // no watch gyroscope available
        }
        // magnetometer
        if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
            // watch magnetometer exists
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            System.out.println("No watch magnetometer"); // no watch magnetometer available
        }

        // Enable display always-on
        setAmbientEnabled();

        try {
            IO.Options options = IO.Options.builder().setTransports(new String[]{WebSocket.NAME}).build();
            // IP address of server must be configured!
            mSocket = IO.socket("http://192.168.178.63:3000"); // IP address Engen

        } catch (Exception e) {
            System.out.println("error: " + e);
        }

        mSocket.on("connect_error", (s) -> {
            System.out.println("connect_error" + s);
        });

        mSocket.on("start recording", (s) -> {
            System.out.println("start recording");
            recording = true;
        });

        mSocket.on("stop recording", (s) -> {
            System.out.println("stop recording");
            recording = false;
        });

        mSocket.on("disconnect", (s) -> {
            mSocket.emit("ciao", s);
            mSocket.connect();
        });

        mSocket.on("connect", (s) -> finallyConnected());
        mSocket.connect();

        // choose the body side, on which wrist the watch is placed
        leftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                side = bodySide.LEFT;
                sideString = side.toString().toLowerCase();
                leftButton.setEnabled(false);
                rightButton.setEnabled(false);
                leftButton.setBackgroundColor(Color.parseColor("#FF018786"));
                mSocket.emit("watch side connect", sideString);
            }
        });
        rightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                side = bodySide.RIGHT;
                sideString = side.toString().toLowerCase();
                leftButton.setEnabled(false);
                rightButton.setEnabled(false);
                rightButton.setBackgroundColor(Color.parseColor("#FF018786"));
                mSocket.emit("watch side connect", sideString);
            }
        });
    }

    private void finallyConnected() {
        System.out.println("id " + mSocket.id());
        startWatchRecording = true;
        mSocket.emit("watch connect", "init");
    }

    private void initializeViews() {
        header = (TextView) findViewById(R.id.header);
        /*currentWatchDataX = (TextView) findViewById(R.id.text_DataX);
        currentWatchDataY = (TextView) findViewById(R.id.text_DataY);
        currentWatchDataZ = (TextView) findViewById(R.id.text_DataZ);*/

        leftButton = (Button) findViewById(R.id.button_left);
        rightButton = (Button) findViewById(R.id.button_right);
    }

    // called when there is a new watch sensor event (e.g. every time when watch accelerometer data has changed)
    @Override
    public void onSensorChanged(SensorEvent event) {
        // accelerometer sensor event
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            values = event.values;
            long timestamp = event.timestamp;
            float accelX = values[0];
            float accelY = values[1];
            float accelZ = values[2];

            /*currentWatchDataX.setText("x: " + accelX);
            currentWatchDataY.setText("y: " + accelY);
            currentWatchDataZ.setText("z: " + accelZ);*/
            if (startWatchRecording && recording) {
                String msg = timestamp + "," + accelX + "," + accelY + "," + accelZ;
                attemptSendWatchAccel(msg);
            }
        }
        // gyroscope sensor event
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroValues = event.values;
            long timestamp = event.timestamp;
            if (recording) {
                attemptSendWatchGyro(timestamp + "," + gyroValues[0] + "," + gyroValues[1] + "," + gyroValues[2]);
            }
        }
        // magnetometer sensor event
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues = event.values;
            long timestamp = event.timestamp;
            if (recording) {
                attemptSendWatchMagnetic(timestamp + "," + magneticValues[0] + "," + magneticValues[1] + "," + magneticValues[2]);
            }
        }
    }

    // methods for sending sensor data to server via WebSocket
    private void attemptSendWatchAccel(String msg) {
        mSocket.emit("watch accel data " + sideString, msg);
    }

    private void attemptSendWatchGyro(String msg) {
        mSocket.emit("watch gyro data " + sideString, msg);
    }

    private void attemptSendWatchMagnetic(String msg) {
        mSocket.emit("watch magnetic data " + sideString, msg);
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //the socket.IO server can send events too
    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String message;
                    try {
                        message = data.getString("message");
                    } catch (JSONException e) {
                        return;
                    }

                    // add the message to view
                    System.out.println(message);
                }
            });
        }
    };

    //close the socket connection and remove all listeners
    @Override
    public void onDestroy() {
        super.onDestroy();
        mSocket.disconnect();
        mSocket.off("new message", onNewMessage);
    }
}