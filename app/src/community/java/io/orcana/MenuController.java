package io.orcana;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;

import java.util.ArrayList;

import timber.log.Timber;

public class MenuController implements SensorEventListener, ButtonManager {
    static final float margin = 2.0f;

    private final MotionMenu menu;
    private final Sensor gyroSensor;
    private final Sensor gravitySensor;
    private final SensorManager mSensorManager;

    float theta = 0.0f;
    double waitTimeStamp = -1d;
    double resetMenuTimeStamp = -1d;
    private boolean isShowing;
    private float menuThreshold;

    public MenuController(Context context, MotionMenu motionMenu) {
        this.menu = motionMenu;
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.gyroSensor = this.mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        this.gravitySensor = this.mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        this.children = new ArrayList<>();
        this.onClickList = new ArrayList<>();
        this.context = context;
//        hide();
        show();
    }

    public void onResume() {
        this.mSensorManager.registerListener(this, this.gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
        this.mSensorManager.registerListener(this, this.gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onPause() {
        this.mSensorManager.unregisterListener(this);
    }

    void show() {
        Timber.d("Showing Menu.");

        this.isShowing = true;
        this.resetMenuTimeStamp = -1d;
        this.menu.showMenu();
    }

    void hide() {
        Timber.d("Hiding Menu.");

        this.isShowing = false;

//        float newThreshold = this.theta + margin;
//        if (newThreshold < margin) {
//            newThreshold = margin;
//        }
//        this.menuThreshold = newThreshold;
        this.menu.hideMenu();
    }

    @SuppressLint("NewApi")
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getStringType()) {
            case Sensor.STRING_TYPE_GYROSCOPE:
                float deltaRotationX = calculateDeltaRotation(event);
                break;

            case Sensor.STRING_TYPE_GRAVITY:
                double currentTimeStamp = ((double) event.timestamp) * CursorView.nanoToSec;

                if(currentTimeStamp < waitTimeStamp){
                    return;
                }

                double x = (double) event.values[0];
                double y = (double) event.values[1];
                double z = (double) -event.values[2];

                float newTheta = (float) Math.atan2(z, Math.sqrt((x * x) + (y * y)));
                newTheta = (float)((180.0/Math.PI) * newTheta);

                float dTheta = this.theta - newTheta;
                this.theta = newTheta;

                if(this.resetMenuTimeStamp < 0.0D){
                    this.resetMenuTimeStamp = currentTimeStamp + CursorView.timeOffset;
                    this.menuThreshold = this.theta + margin;
                }

//                Timber.d(theta + " " + this.menuThreshold + " " + Math.abs(dTheta));
                if(Math.abs(dTheta) <= 5.0f){
                    if(currentTimeStamp > this.resetMenuTimeStamp){
                        this.menuThreshold = this.theta + margin;
                    }
                } else {
                    this.resetMenuTimeStamp = currentTimeStamp + CursorView.timeOffset;
                }


//                if(deltaRotationX < 0.25f){
//                    return;
//                }
                if (this.theta > this.menuThreshold) {
                    waitTimeStamp = currentTimeStamp + 0.1f;
                    if (this.isShowing) {
                        hide();
                    } else  {
                        show();
                    }
                }
//                else {
//                    final float newThreshold = (theta + margin);
//                    menuThreshold = CursorView.clip(newThreshold, minTriggerAngle, menuThreshold);
//                }
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // Create a constant to convert nanoseconds to seconds.
    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    private float timestamp;

    private float calculateDeltaRotation(SensorEvent event){
        // This timestep's delta rotation to be multiplied by the current rotation
        // after computing it from the gyro sample data.
        if (timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * NS2S;
            // Axis of the rotation sample, not normalized yet.
            float axisX = event.values[0];
            float axisY = event.values[1];
            float axisZ = event.values[2];

            // Calculate the angular speed of the sample
            float omegaMagnitude = (float) Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            // (that is, EPSILON should represent your maximum allowable margin of error)
            if (omegaMagnitude > android.util.Half.EPSILON) {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }

            // Integrate around this axis with the angular speed by the timestep
            // in order to get a delta rotation from this sample over the timestep
            // We will convert this axis-angle representation of the delta rotation
            // into a quaternion before turning it into the rotation matrix.
            float thetaOverTwo = omegaMagnitude * dT / 2.0f;
            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
            deltaRotationVector[0] = sinThetaOverTwo * axisX;
            deltaRotationVector[1] = sinThetaOverTwo * axisY;
            deltaRotationVector[2] = sinThetaOverTwo * axisZ;
            deltaRotationVector[3] = cosThetaOverTwo;

            return deltaRotationVector[0];
        }
        timestamp = event.timestamp;
//        float[] deltaRotationMatrix = new float[9];
//        SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
        // User code should concatenate the delta rotation we computed with the current rotation
        // in order to get the updated rotation.
        // rotationCurrent = rotationCurrent * deltaRotationMatrix;
        return 0f;
    }

    final ArrayList<View> children;
    protected Context context;
    final ArrayList<View.OnClickListener> onClickList;

    public void addChild(View child, View.OnClickListener onClick) {
        this.children.add(child);
        if (onClick == null) {
            child.setBackgroundColor(Color.GRAY);
        }
        this.onClickList.add(onClick);
    }

    @Override
    public void clickButton(int buttonID) {
        View.OnClickListener listener = this.onClickList.get(buttonID);
        if (listener != null) {
            View b = this.children.get(buttonID);
            Timber.d("clickButton: %s", b.toString());
            listener.onClick(b);
        }
    }

    @Override
    public int onButton(float x, float y) {
        for(int i = 0; i < this.children.size(); ++i){
            View child = this.children.get(i);
            if (x < ((float) child.getLeft()) || x > ((float) child.getRight()) ||
                    y < ((float) child.getTop()) || y > ((float) child.getBottom())) {
                continue;
            }

            View v = this.children.get(i);
            if(v == null || !v.isEnabled() ||
                    v.getVisibility() == View.INVISIBLE || v.getVisibility() == View.GONE){
                continue;
            }

            if (this.onClickList.get(i) == null) {
                return -1;
            }

            return i;
        }

        return -1;
    }
}