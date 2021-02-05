package io.orcana;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import timber.log.Timber;

public class AnnotationView extends View {
    final Paint paint;

    public AnnotationView(Context context) {
        this(context, null, 0, 0);
    }

    public AnnotationView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public AnnotationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressLint("NewApi")
    public AnnotationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        this.paint = new Paint();
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeWidth(5.0f);
        this.paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void disconnectedFromRoom() {
        clearShapes();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Shape s : shapes.values()) {
            s.onDraw(canvas, this.paint);
        }
    }

    public void addShape(JSONObject annotation, JSONObject roomDimensions) {
        try {
            String category = annotation.getString("category");
            String id = annotation.getString("id");
            switch (category) {
                case "circle":

                    shapes.put(id, new Circle(annotation, roomDimensions));
                    invalidate();
                    break;
                case "rect":
                    shapes.put(id, new Rect(annotation, roomDimensions));
                    invalidate();
                    break;
                case "line":
                    shapes.put(id, new Line(annotation, roomDimensions));
                    invalidate();
                    break;
                case "text":
                    shapes.put(id, new Text(annotation, roomDimensions));
                    invalidate();
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void updateShape(JSONObject annotation, JSONObject roomDimensions) {
        try {
            String id = annotation.getString("id");

            if (shapes.containsKey(id)) {
                Shape s = shapes.get(id);
                s.updateValues(annotation, roomDimensions);
                invalidate();
            } else {
                Timber.d("Could not find Shape with id %s", id);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void removeShape(JSONObject annotation) {
        try {
            String id = annotation.getString("id");

            if (shapes.containsKey(id)) {
                shapes.remove(id);
                invalidate();
            } else {
                Timber.d("Could not find Shape with id %s", id);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void clearShapes() {
        shapes.clear();
        invalidate();
    }

    private final HashMap<String, Shape> shapes = new HashMap<>();

    public void handleScreenshot(JSONObject screenShot, ImageView screenShotView){
        Activity a = getActivity(screenShotView);
        if(a == null){
            return;
        }

        if(screenShot.isNull("src")) {
            a.runOnUiThread(() -> {
                screenShotView.setVisibility(View.INVISIBLE);
                screenShotView.setImageBitmap(null);
            });
        } else {
            try {
                String src = screenShot.getString("src");
                String cleanImage = src.replace("data:image/jpeg;base64,","");
                byte[] decodedString = Base64.decode(cleanImage, Base64.DEFAULT);
                Bitmap bitMap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                a.runOnUiThread(() -> {
                    screenShotView.setImageBitmap(bitMap);
                    screenShotView.setVisibility(View.VISIBLE);
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private Activity getActivity(View view) {
        Context context = view == null ? getContext() : view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        return null;
    }

    private float scaleRadius(float currentRadius, JSONObject roomDimensions) throws JSONException {
        PointF remoteDimensions = new PointF(Double.valueOf(roomDimensions.getDouble("width")).floatValue(),
                                             Double.valueOf(roomDimensions.getDouble("height")).floatValue());
        PointF localDimensions = new PointF(getWidth(), getHeight());

        return currentRadius / remoteDimensions.x * localDimensions.x;
    }

    private PointF scaleCoord(PointF currentPoint, JSONObject roomDimensions) throws JSONException {
        PointF remoteDimensions = new PointF(Double.valueOf(roomDimensions.getDouble("width")).floatValue(),
                                             Double.valueOf(roomDimensions.getDouble("height")).floatValue());
        PointF localDimensions = new PointF(getWidth(), getHeight());

        PointF newCoords = new PointF();
        newCoords.x = currentPoint.x / remoteDimensions.x * localDimensions.x;
        newCoords.y = currentPoint.y / remoteDimensions.y * localDimensions.y;
        return newCoords;
    }

    abstract class Shape {
        int color;
        String id;

        public Shape(JSONObject annotation, JSONObject roomDimensions) throws JSONException {
            this.id = annotation.getString("id");
            this.color = Color.parseColor(annotation.getString("color"));
            updateValues(annotation, roomDimensions);
        }

        abstract public void updateValues(JSONObject annotation, JSONObject roomDimensions) throws JSONException;

        public void onDraw(Canvas canvas, Paint p){
            paint.setColor(color);
        }
    }

    class Circle extends Shape {
        public float cx;
        public float cy;
        public float radius;

        public Circle(JSONObject annotation, JSONObject roomDimensions) throws JSONException {
            super(annotation, roomDimensions);
        }

        @Override
        public void updateValues(JSONObject annotation, JSONObject roomDimensions) throws JSONException {
            PointF currentPointC = new PointF(
                    Double.valueOf(annotation.getDouble("cx")).floatValue(),
                    Double.valueOf(annotation.getDouble("cy")).floatValue()
            );
            PointF scaledPointC = scaleCoord(currentPointC, roomDimensions);

            cx = scaledPointC.x;
            cy = scaledPointC.y;
            radius = scaleRadius(Double.valueOf(annotation.getDouble("r")).floatValue(), roomDimensions);
        }

        @Override
        public void onDraw(Canvas canvas, Paint p) {
            super.onDraw(canvas, p);
            canvas.drawCircle(cx, cy, radius, p);
        }
    }

    class Rect extends Shape {
        public float x;
        public float xOffset;
        public float y;
        public float yOffset;
        public float width;
        public float height;

        public Rect(JSONObject annotation, JSONObject roomDimensions) throws JSONException {
            super(annotation, roomDimensions);
        }

        @Override
        public void updateValues(JSONObject annotation, JSONObject roomDimensions) throws JSONException {
            PointF currentPointR;
            PointF scaledPointR;

            if (annotation.has("transform")) {
                String transform = annotation.getString("transform");
                if (!transform.equals("null")) {
                    String removed = transform.substring(10);
                    int index = removed.indexOf(",");
                    String xString = removed.substring(0, index);
                    String yString = removed.substring(index + 1, removed.indexOf(")"));
                    currentPointR = new PointF(
                            Float.parseFloat(xString),
                            Float.parseFloat(yString)
                    );
                    scaledPointR = scaleCoord(currentPointR, roomDimensions);
                    xOffset = scaledPointR.x;
                    yOffset = scaledPointR.y;
                }
            }

            currentPointR = new PointF(
                    Double.valueOf(annotation.getDouble("x")).floatValue(),
                    Double.valueOf(annotation.getDouble("y")).floatValue()
            );
            scaledPointR = scaleCoord(currentPointR, roomDimensions);
            x = scaledPointR.x;
            y = scaledPointR.y;

            currentPointR = new PointF(
                    Double.valueOf(annotation.getDouble("width")).floatValue(),
                    Double.valueOf(annotation.getDouble("height")).floatValue()
            );
            scaledPointR = scaleCoord(currentPointR, roomDimensions);

            width = scaledPointR.x;
            height = scaledPointR.y;
        }

        @Override
        public void onDraw(Canvas canvas, Paint p) {
            super.onDraw(canvas, p);

            float left = x + xOffset;
            float top = y + yOffset;
            float right = left + width;
            float bottom = top + height;
            canvas.drawRect(left, top, right, bottom, p);
        }
    }

    class Line extends Shape {
        public float x1;
        public float y1;
        public float x2;
        public float y2;

        public Line(JSONObject annotation, JSONObject roomDimensions) throws JSONException {
            super(annotation, roomDimensions);
        }

        @Override
        public void updateValues(JSONObject annotation, JSONObject roomDimensions) throws JSONException {
            PointF currentPoint1 = new PointF(
                    Double.valueOf(annotation.getDouble("x1")).floatValue(),
                    Double.valueOf(annotation.getDouble("y1")).floatValue()
            );
            PointF scaledPoint1 = scaleCoord(currentPoint1, roomDimensions);

            PointF currentPoint2 = new PointF(
                    Double.valueOf(annotation.getDouble("x2")).floatValue(),
                    Double.valueOf(annotation.getDouble("y2")).floatValue()
            );
            PointF scaledPoint2 = scaleCoord(currentPoint2, roomDimensions);

            x1 = scaledPoint1.x;
            y1 = scaledPoint1.y;
            x2 = scaledPoint2.x;
            y2 = scaledPoint2.y;
        }

        @Override
        public void onDraw(Canvas canvas, Paint p) {
            super.onDraw(canvas, p);

            canvas.drawLine(x1, y1, x2, y2, p);

            // Draw Triangle
            int halfWidth = 10 / 2;
            PointF dirNorm = new PointF(x2 - x1, y2 - y1);
            float dirNormValue = Double.valueOf(Math.sqrt((dirNorm.x * dirNorm.x) + (dirNorm.y * dirNorm.y))).floatValue();
            dirNorm.x = (dirNorm.x / dirNormValue) * halfWidth;
            dirNorm.y = (dirNorm.y / dirNormValue) * halfWidth;


            Path path = new Path();
            PointF top = new PointF(x2 + dirNorm.x, y2 + dirNorm.y);
            path.moveTo(top.x, top.y); // Top

            PointF bottom = new PointF(x2 - dirNorm.x, y2 - dirNorm.y);
            PointF dirNormP = new PointF(dirNorm.y, -dirNorm.x);
            path.lineTo(bottom.x - dirNormP.x, bottom.y - dirNormP.y); // Bottom left
            path.lineTo(bottom.x + dirNormP.x, bottom.y + dirNormP.y); // Bottom right

            path.moveTo(top.x, top.y); // Back to Top
            path.close();
            canvas.drawPath(path, p);
        }
    }

    class Text extends Shape {
        public int fontSize;
        public float x;
        public float y;
        public String text;

        public Text(JSONObject annotation, JSONObject roomDimensions) throws JSONException {
            super(annotation, roomDimensions);
            text = annotation.getString("text");
        }

        @Override
        public void updateValues(JSONObject annotation, JSONObject roomDimensions) throws JSONException {
            PointF currentPointT = new PointF(
                    Double.valueOf(annotation.getDouble("x")).floatValue(),
                    Double.valueOf(annotation.getDouble("y")).floatValue()
            );
            PointF scaledPointT = scaleCoord(currentPointT, roomDimensions);

            fontSize = getFontSize(annotation);
            x = scaledPointT.x;
            y = scaledPointT.y;
        }

        @Override
        public void onDraw(Canvas canvas, Paint p) {
            super.onDraw(canvas, p);

            p.setTextSize(fontSize);
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            canvas.drawText(text, x, y, p);
            p.setStyle(Paint.Style.STROKE);
        }

        private int getFontSize(JSONObject annotation) throws JSONException {
            int fontSize = annotation.optInt("fontSize");
            if (fontSize == 0) {
                String fontSizeS = annotation.getString("fontSize");
                fontSize = Integer.parseInt(fontSizeS.substring(0, fontSizeS.indexOf("p")));
            }
            return fontSize;
        }
    }
}