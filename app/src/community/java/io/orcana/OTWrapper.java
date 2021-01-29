package io.orcana;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.twilio.video.app.databinding.RoomActivityBinding;
import com.twilio.video.app.ui.room.RoomActivity;

public class OTWrapper implements MotionMenu {
    private final RoomActivity roomActivity;
    private final RoomActivityBinding binding;

    private final Window window;

    private boolean mute = true;
    private final MenuController menuController;

    public OTWrapper(RoomActivity activity, RoomActivityBinding binding) {
        this.roomActivity = activity;
        this.binding = binding;

        this.window = roomActivity.getWindow();
        WindowManager.LayoutParams layoutparams = this.window.getAttributes();
        layoutparams.flags |= Integer.MIN_VALUE;
        layoutparams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        layoutparams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        this.window.setAttributes(layoutparams);

        // Hide the status bar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final WindowInsetsController insetsController = this.window.getInsetsController();
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.statusBars());
            }
        } else {
            this.window.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
        }

        this.menuController = new MenuController(roomActivity, this);
        setupUI();
    }

    public void onResume() {
        this.menuController.onResume();
    }

    public void onPause() {
        this.menuController.onPause();
    }

    private void setupUI(){
        this.binding.cursorView.setButtonManager(this.menuController);

        this.menuController.addChild(binding.localVideo, k -> binding.localVideo.performClick());
        this.menuController.addChild(binding.localAudio, k -> binding.localAudio.performClick());
    }

    // MotionMenu Implementation
    @Override
    public void showMenu() {
        boolean newMute = !mute;
        if (newMute) {
            setDisplayMute(true);
            this.binding.cursorView.hide();
        } else {
            setDisplayMute(false);
            this.binding.cursorView.show();
        }
    }

    @Override
    public void hideMenu() {}

    public void setDisplayMute(boolean newValue) {
        if (this.mute != newValue) {
            this.mute = newValue;
            WindowManager.LayoutParams layoutparams = this.window.getAttributes();
            if (this.mute) {
                this.binding.blackView.setVisibility(View.VISIBLE);
                layoutparams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
            } else {
                this.binding.blackView.setVisibility(View.INVISIBLE);
                layoutparams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            }
            this.window.setAttributes(layoutparams);
        }
    }
}
