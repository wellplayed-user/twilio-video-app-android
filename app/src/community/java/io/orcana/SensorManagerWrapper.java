package io.orcana;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.WindowManager;

public class SensorManagerWrapper implements SensorEventListener {
    static final double pixPerRad = 1000.0d;
    public static final double nanoToSec = 1.0E-9d;

    final int WIDTH;
    final int HEIGHT;
    final PointF point;
    final Context context;
    final SensorManager mSensorManager;

    double lastUpdate;
    SMWListener smwListener;

    public SensorManagerWrapper(Context context){
        this.context = context;
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        WindowManager windowManager = ((Activity)context).getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        Point m_size = new Point();
        display.getSize(m_size);
        this.WIDTH = m_size.x;
        this.HEIGHT = m_size.y;

        point = new PointF();
        point.x = WIDTH / 2f;
        point.y = HEIGHT / 2f;

//        Correct way?
//        WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
//        Rect displayRect = windowMetrics.getBounds();
//        width = displayRect.width();
//        height = displayRect.height();
    }

    public void show(SMWListener listener){
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_UI);

        this.smwListener = listener;
    }

    public void hide(){
        this.mSensorManager.unregisterListener(this);
        this.smwListener = null;
    }

    public void resetPoint() {
        this.point.x = WIDTH / 2f;
        this.point.y = HEIGHT / 2f;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (Sensor.STRING_TYPE_GYROSCOPE.equals(sensorEvent.sensor.getStringType())) {
            double currentTimeStamp = ((double) sensorEvent.timestamp) * nanoToSec;
            double dt = currentTimeStamp - this.lastUpdate;
            this.lastUpdate = currentTimeStamp;

            if (dt <= 0.3d) {
                double xD = this.point.x;
                double axisY = sensorEvent.values[1];
                float newX = (float) (xD - ((axisY * dt) * pixPerRad));
                newX = clip(newX, this.WIDTH);

                double yD = this.point.y;
                double axisX = sensorEvent.values[0];
                float newY = (float) (yD - ((axisX * dt) * pixPerRad));
                newY = clip(newY, this.HEIGHT);

                this.point.x = newX;
                this.point.y = newY;

                if(this.smwListener != null){
                    this.smwListener.onSensorChanged(currentTimeStamp, this.point);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
}
