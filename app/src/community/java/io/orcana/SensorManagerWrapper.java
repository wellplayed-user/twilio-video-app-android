package io.orcana;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;

import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public class SensorManagerWrapper implements SensorEventListener, com.jorjin.jjsdk.sensor.SensorDataListener {
    public static final double nanoToSec = 1.0E-9d;

    final int WIDTH;
    final int HEIGHT;
    final PointF point;
    final Context context;

    double lastUpdate;
    double pixPerRad;
    SMWListener smwListener;
    SensorManager mSensorManager = null;
    com.jorjin.jjsdk.sensor.SensorManager mJSensorManager = null;

    public SensorManagerWrapper(Context context){
        this.context = context;

        if(DeviceInfo.isPhone()) {
            this.mJSensorManager = new com.jorjin.jjsdk.sensor.SensorManager(context);
            this.mJSensorManager.addSensorDataListener(this);
        } else {
            this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }

        WindowManager windowManager = ((Activity)context).getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        Point m_size = new Point();
        display.getSize(m_size);

        // Switch when in landscape
        if(DeviceInfo.isHeadset()){
            this.WIDTH = m_size.x;
            this.HEIGHT = m_size.y;
            pixPerRad = 1000.0d;
        } else {
            this.WIDTH = m_size.y;
            this.HEIGHT = m_size.x;
            pixPerRad = 50.0d;
        }

        point = new PointF();
        point.x = WIDTH / 2f;
        point.y = HEIGHT / 2f;

//        Correct way?
//        WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
//        Rect displayRect = windowMetrics.getBounds();
//        width = displayRect.width();
//        height = displayRect.height();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if(this.mJSensorManager != null) {
            this.mJSensorManager.removeSensorDataListener(this);
            this.mJSensorManager.release();
        }
    }

    public void show(SMWListener listener){
        if(DeviceInfo.isPhone()) {
            this.mJSensorManager.open(com.jorjin.jjsdk.sensor.SensorManager.SENSOR_TYPE_GYROMETER_3D);
        } else {
            mSensorManager.registerListener(this,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                    SensorManager.SENSOR_DELAY_UI);
        }
        this.smwListener = listener;
    }

    public void hide(){
        if(DeviceInfo.isPhone()) {
            this.mJSensorManager.close(com.jorjin.jjsdk.sensor.SensorManager.SENSOR_TYPE_GYROMETER_3D);
        } else {
            this.mSensorManager.unregisterListener(this);
        }
        this.smwListener = null;
    }

    public void resetPoint() {
        this.point.x = WIDTH / 2f;
        this.point.y = HEIGHT / 2f;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (Sensor.STRING_TYPE_GYROSCOPE.equals(sensorEvent.sensor.getStringType())) {
            sensorUpdate(sensorEvent.values);
        }
    }

    @Override
    public void onSensorDataChanged(int type, float[] values, long timestamp) {
        if(type == com.jorjin.jjsdk.sensor.SensorManager.SENSOR_TYPE_GYROMETER_3D){
//            Timber.d("Data[]: %f, %f, %f", values[0], values[1], values[2]);
            sensorUpdate(values);
        }
    }

    private void sensorUpdate(float[] values) {
        long timestamp = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
        double currentTimeStamp = ((double) timestamp) * nanoToSec;
        double dt = currentTimeStamp - this.lastUpdate;

        if (dt <= 0.3d) {
            double xD = this.point.x;
            double axisY = values[1];
            float newX = (float) (xD - ((axisY * dt) * pixPerRad));
            newX = clip(newX, this.WIDTH);

            double yD = this.point.y;
            double axisX = values[0];
            float newY = (float) (yD - ((axisX * dt) * pixPerRad));
            newY = clip(newY, this.HEIGHT);

            this.point.x = newX;
            this.point.y = newY;

            if(this.smwListener != null){
                this.smwListener.onSensorChanged(currentTimeStamp, this.point);
            }
        }

        this.lastUpdate = currentTimeStamp;
    }

    private static float clip(float a, float max) {
        if (a <= 0.0f) {
            return 0.0f;
        }
        return Math.min(a, max);
    }

    public interface SMWListener {
        void onSensorChanged(double currentTimeStamp, PointF newCursorPoint);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
