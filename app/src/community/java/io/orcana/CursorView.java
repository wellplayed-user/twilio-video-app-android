package io.orcana;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import androidx.core.content.res.ResourcesCompat;

import com.twilio.video.app.R;

import timber.log.Timber;

public class CursorView extends View implements SensorEventListener {
    static final double clickWait = 5000.0d; // In milliseconds
    static final double pixPerRad = 1000.0d;

    public static final double nanoToSec = 1.0E-9d;
    public static final double resetTimeOffset = (clickWait/1000.0d) + 1.0d;

    final int WIDTH;
    final int HEIGHT;
    final int hoverSize;
    final int cursorSize;
    final Paint circlePaint;
    final Paint innerCirclePaint;
    final RectF hoverBox;
    final RectF cursorBox;
    final SensorManager mSensorManager;

    boolean active;
    int buttonId;
    float x;
    float y;
    long startGaze;
    double lastUpdate;
    double resetCursorTimeStamp;
    ButtonManager buttonManager;

    public CursorView(Context context) {
        this(context, null, 0);
    }

    public CursorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CursorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.buttonId = -1;

        int width = 0;
        int height = 0;
        if (!isInEditMode()) {
            WindowManager windowManager = ((Activity)context).getWindowManager();
            Display display = windowManager.getDefaultDisplay();
            Point m_size = new Point();
            display.getSize(m_size);
            width = m_size.x;
            height = m_size.y;

//            Correct way?
//            WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
//            Rect displayRect = windowMetrics.getBounds();
//            width = displayRect.width();
//            height = displayRect.height();
        }

        this.WIDTH = width;
        this.HEIGHT = height;

        this.cursorSize = 15;
        this.cursorBox = new RectF(-cursorSize, -cursorSize, cursorSize -1, cursorSize -1);

        this.hoverSize = this.cursorSize-3;
        this.hoverBox = new RectF(-hoverSize, -hoverSize, hoverSize -1, hoverSize -1);

        this.circlePaint = new Paint();
        this.innerCirclePaint = new Paint();

        if (isInEditMode()) {
            this.mSensorManager = null;
            return;
        }
        setWillNotDraw(false);
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        this.circlePaint.setStyle(Style.STROKE);
        this.circlePaint.setStrokeWidth(5.0f);
        this.circlePaint.setStrokeCap(Cap.ROUND);

        this.circlePaint.setColor(ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));

        this.innerCirclePaint.setStyle(Style.FILL);
        this.innerCirclePaint.setColor(ResourcesCompat.getColor(getResources(), R.color.aquaBlue, null));
//        hide();
        show();
    }

    public void setButtonManager(ButtonManager bm) {
        this.buttonManager = bm;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        if (this.active) {
//            Log.d(TAG, "onDraw: " + this.x + " " + this.y);
            c.translate(this.x, this.y);
            c.drawArc(this.cursorBox, 0.0f, 360.0f, false, this.circlePaint);

            if (this.buttonManager != null) {
                int currentButton = this.buttonManager.onButton(this.x, this.y);
                if (currentButton < 0) {
                    this.startGaze = 0;
                    this.buttonId = -1;
                } else if (currentButton != this.buttonId) {
                    this.startGaze = System.currentTimeMillis();
                    this.buttonId = currentButton;
                } else {
                    float progress;
                    if (this.startGaze < 0) {
                        progress = 1.0f;
                    } else {
                        double dt = (double) (System.currentTimeMillis() - this.startGaze);
                        progress = (float) (dt / clickWait);
                        if (progress > 1.0f) {
                            this.buttonManager.clickButton(this.buttonId);
                            this.startGaze = -1;
                        }
                    }
                    c.drawArc(this.hoverBox, -90.0f, 360.0f * progress, true, this.innerCirclePaint);
                }
            }
        }
    }

    public void hide() {
        this.mSensorManager.unregisterListener(this);
        this.active = false;
        invalidate();
//        this.main.runOnUiThread(new Runnable() {
//            public void run() {
//                CursorView.this.invalidate();
//            }
//        });
    }

    public void show() {
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_UI);
        this.lastUpdate = -1.0d;
        this.active = true;
    }

    public void changeColor(boolean hover){
        if(hover){
            this.circlePaint.setColor(ResourcesCompat.getColor(getResources(), R.color.orcanaBlack, null));
        } else {
            this.circlePaint.setColor(ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (Sensor.STRING_TYPE_GYROSCOPE.equals(sensorEvent.sensor.getStringType())) {
            double currentTimeStamp = ((double) sensorEvent.timestamp) * nanoToSec;
            if (this.lastUpdate < 0.0d) {
                this.lastUpdate = currentTimeStamp;
                this.resetCursorTimeStamp = currentTimeStamp + resetTimeOffset;
                this.x = WIDTH / 2f;
                this.y = HEIGHT / 2f;
                return;
            }

            double dt = currentTimeStamp - this.lastUpdate;
            this.lastUpdate = currentTimeStamp;
            if (dt <= 0.3d) {
                double xD = this.x;
                double axisY = sensorEvent.values[1];
                float newX = (float) (xD - ((axisY * dt) * pixPerRad));
                newX = clip(newX, this.WIDTH);

                double yD = this.y;
                double axisX = sensorEvent.values[0];
                float newY = (float) (yD - ((axisX * dt) * pixPerRad));
                newY = clip(newY, this.HEIGHT);

                float dx = this.x - newX;
                float dy = this.y - newY;
                float sqrMag = (dx * dx) + (dy * dy);

//                Timber.d("SqrMag: %s currentTimeStamp: %s resetCursorTimeStamp: %s", sqrMag, currentTimeStamp, resetCursorTimeStamp);
                if (sqrMag <= 20.0f) {
                    if (currentTimeStamp > this.resetCursorTimeStamp) {
                        this.lastUpdate = -1.0D;
                    }
                } else {
//                        Log.d(TAG, "onSensorChanged: sqrMag " + sqrMag);
                    this.resetCursorTimeStamp = currentTimeStamp + resetTimeOffset;

                    this.x = newX;
                    this.y = newY;
                }
                invalidate();
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
}
