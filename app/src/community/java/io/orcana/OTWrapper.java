package io.orcana;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.google.zxing.WriterException;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.TwilioException;
import com.twilio.video.app.R;
import com.twilio.video.app.data.Preferences;
import com.twilio.video.app.databinding.RoomActivityBinding;
import com.twilio.video.app.ui.room.RoomActivity;
import com.twilio.video.app.ui.room.RoomEvent;

import timber.log.Timber;

public class OTWrapper implements MotionMenu {
    private final RoomActivity roomActivity;
    private final RoomActivityBinding binding;

    private final Window window;

    private boolean menuMute = false;
//    private final MenuController menuController;
    private final ButtonManager buttonManager;

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

//        this.menuController = new MenuController(roomActivity, this, binding.cursorView);
        this.buttonManager = new ButtonManager();
        setupUI(sharedPreferences);

        dataTrackLayer = new DataTrackLayer(this, roomActivity, binding);
        roomActivity.getRoomViewModel().setOrcana(this);

        // Hack to connect to room without button clicks
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            binding.joinO.performClick();
        }, 1500);
    }

    public void onStart(){
        if(DeviceInfo.isTablet()){
            binding.QRCodeLayout.setVisibility(View.VISIBLE);
            binding.qrContinue.setOnClickListener(this::hideQRCodeLayout);

            Editable text = binding.joinRoom.roomName.getText();
            if (text != null) {
                String caseID = text.toString();
                try {
                    Bitmap bitmap = QRCodeGenerator.getQRCodeImage(caseID, 512, 512);
                    binding.QRCodeView.setImageBitmap(bitmap);
                } catch (WriterException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void hideQRCodeLayout(View view){
        binding.QRCodeLayout.setVisibility(View.GONE);
    }

//    public void onResume() {
//        this.menuController.onResume();
//    }

//    public void onPause() {
//        this.menuController.onPause();
//    }

    public void onDestroy() {
        this.dataTrackLayer.onDestroy();
    }

    private void setupUI(SharedPreferences sharedPreferences){
        binding.joinRoom.roomName.setText(sharedPreferences.getString(Preferences.CASE_ID, null));

        this.binding.cursorView.setButtonManager(this.buttonManager);

        this.buttonManager.addChild(binding.joinO, k -> binding.joinO.performClick());
        this.binding.joinO.setOnClickListener(view -> joinRoomClick());

        this.buttonManager.addChild(binding.disconnectO, k->binding.disconnectO.performClick());
        this.binding.disconnectO.setOnClickListener(view -> disconnectClick());

        this.buttonManager.addChild(binding.logoutO, k -> binding.logoutO.performClick());
        this.binding.logoutO.setOnClickListener(view -> logoutClick());

        this.buttonManager.addChild(binding.localVideo, k -> binding.localVideo.performClick());
        this.buttonManager.addChild(binding.localAudio, k -> binding.localAudio.performClick());

        this.buttonManager.addChild(this.binding.volumeMute, k -> this.binding.volumeMute.performClick());
        this.binding.volumeMute.setOnClickListener(view -> volumeMuteButtonClick());

        this.buttonManager.addChild(this.binding.volumeMin, k -> this.binding.volumeMin.performClick());
        this.binding.volumeMin.setOnClickListener(view -> volumeMinButtonClick());

        this.buttonManager.addChild(this.binding.volumeMid, k -> this.binding.volumeMid.performClick());
        this.binding.volumeMid.setOnClickListener(view -> volumeMidButtonClick());

        this.buttonManager.addChild(this.binding.volumeLoud, k -> this.binding.volumeLoud.performClick());
        this.binding.volumeLoud.setOnClickListener(view -> volumeLoudButtonClick());

        this.buttonManager.addChild(this.binding.volumeMax, k -> this.binding.volumeMax.performClick());
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

    void setDisplayMute(boolean newValue) {
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

    private boolean isRemoteAudioTrackPlaybackEnabled = true;
    void enableRemoteAudioTrackPlayback(boolean b){
        if(isRemoteAudioTrackPlaybackEnabled == b) return;
        isRemoteAudioTrackPlaybackEnabled = b;

        Room r = roomActivity.getRoomManager().getRoom();
        if(r != null){
            for (RemoteParticipant rp : r.getRemoteParticipants()) {
                enableRemoteParticipantAudioTrackPlayback(b, rp);
            }
        }
    }

    void enableRemoteParticipantAudioTrackPlayback(boolean b, RemoteParticipant rp){
        for (RemoteAudioTrackPublication ratp : rp.getRemoteAudioTracks()) {
            RemoteAudioTrack rat = ratp.getRemoteAudioTrack();
            if(rat != null){
                Timber.d("setting audio playback of %s to %s", rp.getIdentity(), b);
                rat.enablePlayback(b);
            }
        }
    }

    // Button Clicks
    void volumeMuteButtonClick() {
        this.binding.volumeMute.setImageResource(R.drawable.ic_volume_down_green_24px);
        this.binding.volumeMin.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMid.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeLoud.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMax.setImageResource(R.drawable.ic_volume_down_gray_24px);
        enableRemoteAudioTrackPlayback(false);
    }

    void volumeMinButtonClick() {
        enableRemoteAudioTrackPlayback(true);
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
        enableRemoteAudioTrackPlayback(true);
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
        enableRemoteAudioTrackPlayback(true);
        this.binding.volumeMute.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMin.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeMid.setImageResource(R.drawable.ic_volume_down_gray_24px);
        this.binding.volumeLoud.setImageResource(R.drawable.ic_volume_down_green_24px);
        this.binding.volumeMax.setImageResource(R.drawable.ic_volume_down_gray_24px);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVolume / 4) * 3, AudioManager.FLAG_SHOW_UI);
    }

    void volumeMaxButtonClick() {
        enableRemoteAudioTrackPlayback(true);
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
        setDisplayMute(false);
        this.binding.cursorView.show();
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

    // Room Events
    public void connected(RoomEvent.Connected connectedEvent){
        Timber.d("Connected Event: %s", connectedEvent.toString());

        dataTrackLayer.connected(connectedEvent);

        if(DeviceInfo.isTablet()){
            volumeMuteButtonClick();
            binding.localAudio.performClick();
        } else {
            volumeMidButtonClick(false);
        }
    }

    public void disconnected(RoomEvent.Disconnected roomEvent){
        Timber.d("Disconnected Event: %s", roomEvent.toString());
        dataTrackLayer.disconnected(roomEvent);
    }

    public void remoteParticipantConnected(RoomEvent.RemoteParticipantEvent.RemoteParticipantConnected remoteParticipantConnectedEvent){
        Timber.d("Remote Participant Connected Event: %s", remoteParticipantConnectedEvent.toString());
        RemoteParticipant rp = (RemoteParticipant)remoteParticipantConnectedEvent.getParticipant();
        Timber.d("%s connected", rp.getIdentity());

        enableRemoteParticipantAudioTrackPlayback(isRemoteAudioTrackPlaybackEnabled, rp);

        dataTrackLayer.remoteParticipantConnected(remoteParticipantConnectedEvent);
    }

    public void remoteParticipantDisconnected(RoomEvent.RemoteParticipantEvent.RemoteParticipantDisconnected remoteParticipantDisconnected){
        Timber.d("Participant Disconnected Event: %s", remoteParticipantDisconnected.toString());
        dataTrackLayer.remoteParticipantDisconnected(remoteParticipantDisconnected);
    }

    public void onDataTrackSubscribed(RoomEvent.RemoteParticipantEvent.OnDataTrackSubscribed OnDataTrackSubscribedData){
        dataTrackLayer.onDataTrackSubscribed(OnDataTrackSubscribedData);
    }

    public void muteRemoteParticipant(RoomEvent.RemoteParticipantEvent.MuteRemoteParticipant muteRemoteParticipant){
        RemoteParticipant remoteParticipant = null;
        for (RemoteParticipant rp : roomActivity.getRoomManager().getRoom().getRemoteParticipants()) {
            if (rp.getSid().equals(muteRemoteParticipant.getSid())) {
                remoteParticipant = rp;
                break;
            }
        }

        if (remoteParticipant == null) {
            Timber.d("Could not find remote participant with SID %s", muteRemoteParticipant.getSid());
        } else {
            Timber.d("updating %s's audio playback", remoteParticipant.getIdentity());
            enableRemoteParticipantAudioTrackPlayback(isRemoteAudioTrackPlaybackEnabled, remoteParticipant);
        }
    }
}
