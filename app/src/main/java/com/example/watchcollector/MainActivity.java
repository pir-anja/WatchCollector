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
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.text.TextUtils;
import android.widget.TextView;


import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends WearableActivity implements SensorEventListener {

    private TextView header;
    private TextView currentWatchDataX, currentWatchDataY, currentWatchDataZ;
    private boolean startWatchRecording = false;
    private boolean recording = false;

    private Button leftButton;
    private Button rightButton;

    //smartwatch sensor
    private SensorManager sensorManager;
    private Sensor accelerometer;
    Sensor gyroscope;
    Sensor magnetometer;

    private bodySide side = bodySide.NON_DEFINED;

    private String sideString = "";
    private Socket mSocket;

    float[] gyroValues;
    float[] values;
    float[] magneticValues;

   /* {

        try {
            mSocket = IO.socket("http://100.124.115.57:3000");
            //mSocket = IO.socket("http://localhost:3000");

        } catch (Exception e) {
            System.out.println("error: " + e);
        }
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //mSocket.on("new message", onNewMessage);

        setContentView(R.layout.activity_main);

        initializeViews();

        //init sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // watch accelerometer exists
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            System.out.println("No watch accelerometer"); // no watch accelerometer available
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            // phone accelerometer exists
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            System.out.println("No watch gyroscope"); // no phone accelerometer available
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
            // phone accelerometer exists
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            System.out.println("No watch magnetometer"); // no phone accelerometer available
        }

        // Enables Always-on
        setAmbientEnabled();

        try {
            //mSocket = IO.socket("http://100.124.115.57:3000");
            mSocket = IO.socket("http://192.168.178.63:3000");

        } catch (Exception e) {
            System.out.println("error: " + e);
        }

        mSocket.on("connect_error",(s) -> {
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
        mSocket.on("connect", (s) -> finallyConnected());
        mSocket.connect();
/*
        try {
            mSocket = IO.socket("http://100.124.115.57:3000");
        } catch (Exception e) {
            System.out.println("could not create a socket");
        }*/

/*
        mSocket.on("phone data", (s) -> {
            System.out.println("phone data");
        });*/




        //mSocket.on("connect", );
       /* mSocket.on("connect", (s) -> {
            System.out.println("phone data");
        });*/

        leftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
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
            public void onClick(View view)
            {
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
        currentWatchDataX = (TextView) findViewById(R.id.text_DataX);
        currentWatchDataY = (TextView) findViewById(R.id.text_DataY);
        currentWatchDataZ = (TextView) findViewById(R.id.text_DataZ);

        leftButton = (Button) findViewById(R.id.button_left);
        rightButton = (Button) findViewById(R.id.button_right);
    }

    //Called when there is a new watch sensor event (e.g. every time when watch accelerometer data has changed)
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

           values = event.values;
            float accelX = values[0];
            float accelY = values[1];
            float accelZ = values[2];

            currentWatchDataX.setText("x: " + Float.toString(accelX));
            currentWatchDataY.setText("y: " + Float.toString(accelY));
            currentWatchDataZ.setText("z: " + Float.toString(accelZ));
            if (startWatchRecording && recording) {
                //attemptSend();
                String msg = event.timestamp + "," + accelX + "," + accelY + "," + accelZ;
                attemptSendWatchAccel(msg);
                //mSocket.emit("watch accel data", msg);
            }
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroValues = event.values;
            float timestamp = event.timestamp;
            if (recording) {
                attemptSendWatchGyro(timestamp + "," + Float.toString(gyroValues[0]) + "," + Float.toString(gyroValues[1]) + "," + Float.toString(gyroValues[2]));
            }
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues = event.values;
            float timestamp = event.timestamp;
            if (recording) {
                attemptSendWatchMagnetic(timestamp + "," + Float.toString(magneticValues[0]) + "," + Float.toString(magneticValues[1]) + "," + Float.toString(magneticValues[2]));
            }
        }


    }

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

    private void attemptSend() {
        //System.out.println("test");

        String msg = ":)";
        mSocket.emit("phone data", msg);
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