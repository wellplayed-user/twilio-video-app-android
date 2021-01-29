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
            this.paint.setColor(s.color);
            s.onDraw(canvas, this.paint);
        }
    }

    public void addShape(JSONObject annotation) {
        try {
            String category = annotation.getString("category");

            String id = annotation.getString("id");
            int color = Color.parseColor(annotation.getString("color"));

            switch (category) {
                case "circle":
                    shapes.put(id, new Circle(
                            Double.valueOf(annotation.getDouble("cx")).floatValue(),
                            Double.valueOf(annotation.getDouble("cy")).floatValue(),
                            Double.valueOf(annotation.getDouble("r")).floatValue(),
                            color, id
                    ));
                    invalidate();
                    break;
                case "rect":
                    shapes.put(id, new Rect(
                            Double.valueOf(annotation.getDouble("x")).floatValue(),
                            Double.valueOf(annotation.getDouble("y")).floatValue(),
                            Double.valueOf(annotation.getDouble("width")).floatValue(),
                            Double.valueOf(annotation.getDouble("height")).floatValue(),
                            color, id
                    ));
                    invalidate();
                    break;
                case "line":
                    shapes.put(id, new Line(
                            Double.valueOf(annotation.getDouble("x1")).floatValue(),
                            Double.valueOf(annotation.getDouble("y1")).floatValue(),
                            Double.valueOf(annotation.getDouble("x2")).floatValue(),
                            Double.valueOf(annotation.getDouble("y2")).floatValue(),
                            color, id
                    ));
                    invalidate();
                    break;
                case "text":
                    shapes.put(id, new Text(
                            Text.getFontSize(annotation),
                            Double.valueOf(annotation.getDouble("x")).floatValue(),
                            Double.valueOf(annotation.getDouble("y")).floatValue(),
                            annotation.getString("text"),
                            color, id
                    ));
                    invalidate();
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void updateShape(JSONObject annotation) {
        try {
            String id = annotation.getString("id");

            if (shapes.containsKey(id)) {
                Shape s = shapes.get(id);
                if (s instanceof Circle) {
                    Circle c = (Circle) s;
                    c.cx = Double.valueOf(annotation.getDouble("cx")).floatValue();
                    c.cy = Double.valueOf(annotation.getDouble("cy")).floatValue();
                    c.radius = Double.valueOf(annotation.getDouble("r")).floatValue();
                    invalidate();
                } else if (s instanceof Rect) {
                    Rect r = (Rect) s;

                    if (annotation.has("transform")) {
                        String transform = annotation.getString("transform");
                        if (!transform.equals("null")) {
                            String removed = transform.substring(10);
                            int index = removed.indexOf(",");
                            String xString = removed.substring(0, index);
                            r.xOffset = Float.parseFloat(xString);
                            String yString = removed.substring(index + 1, removed.indexOf(")"));
                            r.yOffset = Float.parseFloat(yString);
                        }
                    }

                    r.x = Double.valueOf(annotation.getDouble("x")).floatValue();
                    r.y = Double.valueOf(annotation.getDouble("y")).floatValue();
                    r.width = Double.valueOf(annotation.getDouble("width")).floatValue();
                    r.height = Double.valueOf(annotation.getDouble("height")).floatValue();
                    invalidate();
                } else if (s instanceof Line) {
                    Line l = (Line) s;
                    l.x1 = Double.valueOf(annotation.getDouble("x1")).floatValue();
                    l.y1 = Double.valueOf(annotation.getDouble("y1")).floatValue();
                    l.x2 = Double.valueOf(annotation.getDouble("x2")).floatValue();
                    l.y2 = Double.valueOf(annotation.getDouble("y2")).floatValue();
                    invalidate();
                } else if (s instanceof Text) {
                    Text t = (Text) s;
                    t.fontSize = Text.getFontSize(annotation);
                    t.x = Double.valueOf(annotation.getDouble("x")).floatValue();
                    t.y = Double.valueOf(annotation.getDouble("y")).floatValue();
                    invalidate();
                }
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
        if(screenShot.isNull("src")) {
            getActivity(screenShotView).runOnUiThread(() -> {
                screenShotView.setVisibility(View.INVISIBLE);
                screenShotView.setImageBitmap(null);
            });
        } else {
            try {
                String src = screenShot.getString("src");
                String cleanImage = src.replace("data:image/jpeg;base64,","");
                byte[] decodedString = Base64.decode(cleanImage, Base64.DEFAULT);
                Bitmap bitMap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                getActivity(screenShotView).runOnUiThread(() -> {
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
}

abstract class Shape {
    int color;
    String id;

    public Shape(int c, String id) {
        this.color = c;
        this.id = id;
    }

    abstract public void onDraw(Canvas canvas, Paint p);
}

class Circle extends Shape {
    public float cx;
    public float cy;
    public float radius;

    public Circle(float x, float y, float r, int color, String id) {
        super(color, id);
        this.cx = x;
        this.cy = y;
        this.radius = r;
    }

    @Override
    public void onDraw(Canvas canvas, Paint p) {
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

    public Rect(float x, float y, float w, float h, int c, String id) {
        super(c, id);
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
    }

    @Override
    public void onDraw(Canvas canvas, Paint p) {
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

    public Line(float x1, float y1, float x2, float y2, int c, String id) {
        super(c, id);
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    @Override
    public void onDraw(Canvas canvas, Paint p) {
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

    public Text(int fontSize, float x, float y, String text, int c, String id) {
        super(c, id);
        this.fontSize = fontSize;
        this.x = x;
        this.y = y;
        this.text = text;
    }

    public static int getFontSize(JSONObject annotation) throws JSONException {
        int fontSize = annotation.optInt("fontSize");
        if (fontSize == 0) {
            String fontSizeS = annotation.getString("fontSize");
            fontSize = Integer.parseInt(fontSizeS.substring(0, fontSizeS.indexOf("p")));
        }
        return fontSize;
    }

    @Override
    public void onDraw(Canvas canvas, Paint p) {
        p.setTextSize(fontSize);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawText(text, x, y, p);
        p.setStyle(Paint.Style.STROKE);
    }
}