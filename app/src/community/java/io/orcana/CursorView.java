package io.orcana;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.twilio.video.app.R;

import timber.log.Timber;

public class CursorView extends View {
    static final double clickWait = 5000.0d; // In milliseconds
    public static final double resetTimeOffset = (clickWait/1000.0d) + 1.0d;

    final int hoverSize;
    final int cursorSize;
    final Paint circlePaint;
    final Paint innerCirclePaint;
    final RectF hoverBox;
    final RectF cursorBox;
    final SensorManagerWrapper mSensorManagerWrapper;

    boolean active;
    int buttonId;
    float x;
    float y;
    long startGaze;
    double lastUpdate;
    double resetCursorTimeStamp;
    IButtonManager buttonManager;

    public CursorView(Context context) {
        this(context, null, 0);
    }

    public CursorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CursorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.buttonId = -1;

        this.cursorSize = 15;
        this.cursorBox = new RectF(-cursorSize, -cursorSize, cursorSize -1, cursorSize -1);

        this.hoverSize = this.cursorSize-3;
        this.hoverBox = new RectF(-hoverSize, -hoverSize, hoverSize -1, hoverSize -1);

        this.circlePaint = new Paint();
        this.innerCirclePaint = new Paint();

        this.mSensorManagerWrapper = new SensorManagerWrapper(context);
        if (isInEditMode()) {
//            this.mSensorManagerWrapper = null;
            return;
        }

        setWillNotDraw(false);

        this.circlePaint.setStyle(Style.STROKE);
        this.circlePaint.setStrokeWidth(5.0f);
        this.circlePaint.setStrokeCap(Cap.ROUND);

        this.circlePaint.setColor(ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));

        this.innerCirclePaint.setStyle(Style.FILL);
        this.innerCirclePaint.setColor(ResourcesCompat.getColor(getResources(), R.color.aquaBlue, null));
//        hide();
        show();
    }

    public void setButtonManager(IButtonManager bm) {
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
        this.active = false;
        this.mSensorManagerWrapper.hide();
        invalidate();
    }

    static final float CursorMovementResetOffset = 1000.f;
    public void show() {
        this.active = true;
        this.lastUpdate = -1.0d;
        this.mSensorManagerWrapper.show((currentTimeStamp, newCursorPoint) -> {
            if (this.lastUpdate < 0.0d) {
                this.lastUpdate = currentTimeStamp;
                this.resetCursorTimeStamp = currentTimeStamp + resetTimeOffset;
                this.mSensorManagerWrapper.resetPoint();
                return;
            }

            double dt = currentTimeStamp - this.lastUpdate;
            this.lastUpdate = currentTimeStamp;
            if (dt <= 0.3d) {
                float dx = this.x - newCursorPoint.x;
                float dy = this.y - newCursorPoint.y;
                float sqrMag = (dx * dx) + (dy * dy);

                if (sqrMag <= CursorMovementResetOffset) {
                    if (currentTimeStamp > this.resetCursorTimeStamp) {
                        this.lastUpdate = -1.0D;
                    }
                } else {
                    this.resetCursorTimeStamp = currentTimeStamp + resetTimeOffset;
                }

                this.x = newCursorPoint.x;
                this.y = newCursorPoint.y;
                invalidate();
            }
        });
    }

    public void changeColor(boolean hover){
        if(hover){
            this.circlePaint.setColor(ResourcesCompat.getColor(getResources(), R.color.orcanaBlack, null));
        } else {
            this.circlePaint.setColor(ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
        }
    }
}
