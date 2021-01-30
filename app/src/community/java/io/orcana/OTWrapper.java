package io.orcana;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.twilio.video.Room;
import com.twilio.video.app.R;
import com.twilio.video.app.data.Preferences;
import com.twilio.video.app.databinding.RoomActivityBinding;
import com.twilio.video.app.sdk.RoomManager;
import com.twilio.video.app.ui.room.RoomActivity;

import timber.log.Timber;

public class OTWrapper implements MotionMenu {
    private final RoomActivity roomActivity;
    private final RoomActivityBinding binding;

    private final Window window;

    private boolean menuMute = true;
    private final MenuController menuController;

    private final int maxVolume;
    private final AudioManager audioManager;

    private final DataTrackLayer dataTrackLayer;

    public OTWrapper(RoomActivity activity, RoomActivityBinding binding, SharedPreferences sharedPreferences) {
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

        audioManager = (AudioManager) roomActivity.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        volumeMidButtonClick(false);

        this.menuController = new MenuController(roomActivity, this);
        setupUI(sharedPreferences);

        dataTrackLayer = new DataTrackLayer(roomActivity, binding);

        // Hack to connect to room without button clicks
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
//            roomActivity.switchCamera();
//            roomActivity.toggleLocalVideo();
            binding.joinO.performClick();
        }, 1500);
    }

    public void onResume() {
        this.menuController.onResume();
    }

    public void onPause() {
        this.menuController.onPause();
    }

    public void onDestroy() {
        this.dataTrackLayer.onDestroy();
    }

    private void setupUI(SharedPreferences sharedPreferences){
        binding.joinRoom.roomName.setText(sharedPreferences.getString(Preferences.CASE_ID, null));

        this.binding.cursorView.setButtonManager(this.menuController);

        this.menuController.addChild(binding.joinO, k -> binding.joinO.performClick());
        this.binding.joinO.setOnClickListener(view -> joinRoomClick());

        this.menuController.addChild(binding.disconnectO, k->binding.disconnectO.performClick());
        this.binding.disconnectO.setOnClickListener(view -> disconnectClick());

        this.menuController.addChild(binding.logoutO, k -> binding.logoutO.performClick());
        this.binding.logoutO.setOnClickListener(view -> logoutClick());

        this.menuController.addChild(binding.localVideo, k -> binding.localVideo.performClick());
        this.menuController.addChild(binding.localAudio, k -> binding.localAudio.performClick());

        this.menuController.addChild(this.binding.volumeMute, k -> this.binding.volumeMute.performClick());
        this.binding.volumeMute.setOnClickListener(view -> volumeMuteButtonClick());

        this.menuController.addChild(this.binding.volumeMin, k -> this.binding.volumeMin.performClick());
        this.binding.volumeMin.setOnClickListener(view -> volumeMinButtonClick());

        this.menuController.addChild(this.binding.volumeMid, k -> this.binding.volumeMid.performClick());
        this.binding.volumeMid.setOnClickListener(view -> volumeMidButtonClick());

        this.menuController.addChild(this.binding.volumeLoud, k -> this.binding.volumeLoud.performClick());
        this.binding.volumeLoud.setOnClickListener(view -> volumeLoudButtonClick());

        this.menuController.addChild(this.binding.volumeMax, k -> this.binding.volumeMax.performClick());
        this.binding.volumeMax.setOnClickListener(view -> volumeMaxButtonClick());
    }

    // MotionMenu Implementation
    @Override
    public void showMenu() {
        boolean newValue = !menuMute;
        if (newValue) {
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
        if (this.menuMute != newValue) {
            this.menuMute = newValue;
            WindowManager.LayoutParams layoutparams = this.window.getAttributes();
            if (this.menuMute) {
                this.binding.blackView.setVisibility(View.VISIBLE);
                layoutparams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
            } else {
                this.binding.blackView.setVisibility(View.INVISIBLE);
                layoutparams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            }
            this.window.setAttributes(layoutparams);
        }
    }

    // Button Clicks
    void volumeMuteButtonClick() {
        this.binding.volumeMute.setImageResource(R.drawable.ic_volume_down_green_24px);
        this.binding.volumeMin.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMid.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeLoud.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMax.setImageResource(R.drawable.ic_volume_down_gray_24px);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, AudioManager.FLAG_SHOW_UI);
    }

    void volumeMinButtonClick() {
        this.binding.volumeMute.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMin.setImageResource(R.drawable.ic_volume_down_green_24px);
        this.binding.volumeMid.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeLoud.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMax.setImageResource(R.drawable.ic_volume_down_gray_24px);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVolume / 4), AudioManager.FLAG_SHOW_UI);
    }

    void volumeMidButtonClick() {
        volumeMidButtonClick(true);
    }

    void volumeMidButtonClick(boolean showUI) {
        this.binding.volumeMute.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMin.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMid.setImageResource(R.drawable.ic_volume_down_green_24px);
        this.binding.volumeLoud.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMax.setImageResource(R.drawable.ic_volume_down_gray_24px);
        if (showUI) {
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVolume / 4) * 2, AudioManager.FLAG_SHOW_UI);
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVolume / 4) * 2, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        }
    }

    void volumeLoudButtonClick() {
        this.binding.volumeMute.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMin.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMid.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeLoud.setImageResource(R.drawable.ic_volume_down_green_24px);
        this.binding.volumeMax.setImageResource(R.drawable.ic_volume_down_gray_24px);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVolume / 4) * 3, AudioManager.FLAG_SHOW_UI);
    }

    void volumeMaxButtonClick() {
        this.binding.volumeMute.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMin.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMid.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeLoud.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMax.setImageResource(R.drawable.ic_volume_down_green_24px);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, AudioManager.FLAG_SHOW_UI);
    }

    void joinRoomClick() {
        Timber.d("joinroom");
        binding.joinRoom.connect.performClick();
    }

    void disconnectClick() {
        Timber.d("disconnect");
        this.binding.disconnect.performClick();
    }

    void logoutClick() {
        Timber.d("logout");
        roomActivity.logout();
    }

    public void updateUI(int joinLogoutButtonState, int disconnectButtonState) {
        binding.joinO.setVisibility(joinLogoutButtonState);
        binding.disconnectO.setVisibility(disconnectButtonState);
        binding.logoutO.setVisibility(joinLogoutButtonState);
    }
}
