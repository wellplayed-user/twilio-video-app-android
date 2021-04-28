package io.orcana;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import com.jorjin.jjsdk.camera.CameraManager;
import com.jorjin.jjsdk.camera.CameraParameter;
import com.jorjin.jjsdk.camera.FrameListener;
import com.twilio.video.Rgba8888Buffer;
import com.twilio.video.VideoCapturer;
import com.twilio.video.VideoDimensions;
import com.twilio.video.VideoFormat;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;
import tvi.webrtc.CapturerObserver;
import tvi.webrtc.SurfaceTextureHelper;
import tvi.webrtc.VideoFrame;

// Built based on https://github.com/twilio/video-quickstart-android/tree/master/exampleCustomVideoCapturer/src/main/java/com/twilio/video/examples/customcapturer
public class JorJinCameraCapturer implements VideoCapturer, FrameListener {
    private static final int ResolutionIndex = 2;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private CameraManager cameraManager;
    private CapturerObserver capturerObserver;

    public JorJinCameraCapturer(Context context) {
        this.cameraManager = new CameraManager(context);
        this.cameraManager.setCameraFrameListener(this);

//        CameraParameter cameraParameter = cameraManager.getCameraParameter();
//        cameraParameter.setAutoFocus(true);
//        cameraParameter.setBrightness(22);
//        cameraParameter.setContrast(43);
//        cameraParameter.setSharpness(14);
//        cameraParameter.setGamma(65);
//        cameraParameter.setHue(60);
//        cameraParameter.setSaturation(30);
//        cameraParameter.setPowerLineFrequency(CameraParameter.POWER_LINE_60HZ);
//        this.cameraManager.setCameraParameter(cameraParameter);

        this.cameraManager.setResolutionIndex(ResolutionIndex);
        this.cameraManager.startCamera(CameraManager.COLOR_FORMAT_RGBA);
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        Timber.d("startCapture!!");

        this.started.set(true);
        this.capturerObserver.onCapturerStarted(true);
    }

    @Override
    public void stopCapture() {
        Timber.d("stopCapture!!");

        this.started.set(false);
        this.capturerObserver.onCapturerStopped();
    }

    @Override
    public void dispose() {
        Timber.d("dispose!!");

        if(this.cameraManager != null && this.cameraManager.isPreviewing()){
            this.cameraManager.stopCamera();
        }
        this.cameraManager = null;
    }

    @Override
    public void onIncomingFrame(ByteBuffer byteBuffer, int width, int height, int format) {
        boolean dropFrame = width == 0 || height == 0 || !this.started.get();

        // Only capture the view if the dimensions have been established
        if (!dropFrame) {
            // Extract the frame from the bitmap
            Bitmap convertedImg = bitmapFromRgba(width, height, byteBuffer.array());
            int bytes = convertedImg.getByteCount();
            ByteBuffer buffer = ByteBuffer.allocate(bytes);
            convertedImg.copyPixelsToBuffer(buffer);

            final long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
            VideoFrame.Buffer videoBuffer = new Rgba8888Buffer(buffer ,width, height);
            VideoFrame videoFrame = new VideoFrame(videoBuffer, 0, captureTimeNs);

            this.capturerObserver.onFrameCaptured(videoFrame);
            videoFrame.release();
        }
    }

    @Override
    public VideoFormat getCaptureFormat() {
        String[] var1 = this.cameraManager.getResolutionList()[ResolutionIndex].split("x");
        int width = Integer.parseInt(var1[0]);
        int height = Integer.parseInt(var1[1]);
        VideoDimensions videoDimensions = new VideoDimensions(width, height);

        int frameRate = this.cameraManager.getCameraParameter().getPowerLineFrequency();
        return new VideoFormat(videoDimensions, frameRate);

//        return new VideoFormat(VideoDimensions.HD_1080P_VIDEO_DIMENSIONS, 30);
    }

    @Override
    public boolean isScreencast() {
        return true;
    }

    private static Bitmap bitmapFromRgba(int width, int height, byte[] bytes) {
        int[] pixels = new int[bytes.length / 4];
        int j = 0;

        for (int i = 0; i < pixels.length; i++) {
            int R = bytes[j++] & 0xff;
            int G = bytes[j++] & 0xff;
            int B = bytes[j++] & 0xff;
            int A = bytes[j++] & 0xff;

            int pixel = (A << 24) | (R << 16) | (G << 8) | B;
            pixels[i] = pixel;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}
