package io.orcana;

import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.widget.ImageView;

import com.twilio.video.LocalParticipant;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.Room;
import com.twilio.video.app.sdk.RoomManager;
import com.twilio.video.app.ui.room.RoomActivity;
import com.twilio.video.app.ui.room.RoomEvent;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import com.twilio.video.app.databinding.RoomActivityBinding;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import timber.log.Timber;

public class DataTrackLayer {
    private static final String DATA_TRACK_MESSAGE_THREAD_NAME = "DataTrackMessages";

    private Room room;
    private final OTWrapper orcana;
    private final RoomManager roomManager;
    private final RoomActivity roomActivity;

    private final RoomActivityBinding binding;
    private final ImageView screenshotView;
    private final AnnotationView annotationView;

    // Dedicated thread and handler for messages received from a RemoteDataTrack
    private final HandlerThread dataTrackMessageThread =
            new HandlerThread(DATA_TRACK_MESSAGE_THREAD_NAME);
    private final Handler dataTrackMessageThreadHandler;

    // Map used to map remote data tracks to remote participants
//    private final Map<RemoteDataTrack, RemoteParticipant> dataTrackRemoteParticipantMap =
//            new HashMap<>();

    public DataTrackLayer(OTWrapper otWrapper, RoomActivity roomActivity, RoomActivityBinding binding) {
        this.orcana = otWrapper;
        this.roomActivity = roomActivity;
        this.roomManager = roomActivity.getRoomManager();
        
        this.binding = binding;
        this.annotationView = binding.annotationView;
        this.screenshotView = binding.screenshotView;

        roomActivity.getRoomViewModel().setDataTrackLayer(this);
        // Start the thread where data messages are received
        dataTrackMessageThread.start();
        dataTrackMessageThreadHandler = new Handler(dataTrackMessageThread.getLooper());
    }

    public void onDestroy() {
        disconnectFromRoom();

        // Quit the data track message thread
        dataTrackMessageThread.quit();
    }

    public void connected(RoomEvent.Connected connectedEvent){
        Timber.d("Connected Event: %s", connectedEvent.toString());
//        Timber.d("Start sending messages?");

        this.room = connectedEvent.getRoom();

        for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
            addRemoteParticipant(remoteParticipant);
        }
    }

    public  void disconnected(RoomEvent.Disconnected roomEvent){
        Timber.d("Disconnected Event: %s", roomEvent.toString());
        disconnectFromRoom();
    }

    public void remoteParticipantConnected(RoomEvent.RemoteParticipantEvent.RemoteParticipantConnected remoteParticipantConnectedEvent){
        Timber.d("Remote Participant Connected Event: %s", remoteParticipantConnectedEvent.toString());
        Timber.d("%s connected", remoteParticipantConnectedEvent.getParticipant().getIdentity());
        addRemoteParticipant((RemoteParticipant) remoteParticipantConnectedEvent.getParticipant());
    }

    public void remoteParticipantDisconnected(RoomEvent.RemoteParticipantEvent.RemoteParticipantDisconnected remoteParticipantDisconnected){
        Timber.d("Participant Disconnected Event: %s", remoteParticipantDisconnected.toString());

        RemoteParticipant remoteParticipant = null;
        List<RemoteParticipant> remoteParticipantList = Objects.requireNonNull(roomManager.getRoom()).getRemoteParticipants();
        for (RemoteParticipant rp : remoteParticipantList) {
            if (rp.getSid().equals(remoteParticipantDisconnected.getSid())) {
                remoteParticipant = rp;
                break;
            }
        }

        if (remoteParticipant == null) {
            Timber.d("Could not find remote participant with SID %s", remoteParticipantDisconnected.getSid());
        } else {
            Timber.d("%s disconnected", remoteParticipant.getIdentity());
//            removeRemoteParticipant(remoteParticipant);
        }
    }

    public void onDataTrackSubscribed(RoomEvent.RemoteParticipantEvent.OnDataTrackSubscribed OnDataTrackSubscribedData) {
        /*
         * Data track messages are received on the thread that calls setListener. Post the
         * invocation of setting the listener onto our dedicated data track message thread.
         */
        dataTrackMessageThreadHandler.post(() -> addRemoteDataTrack(OnDataTrackSubscribedData.getRemoteParticipant(), OnDataTrackSubscribedData.getRemoteDataTrack()));
    }

    public void disconnectFromRoom() {
        annotationView.disconnectedFromRoom();
    }

    private void addRemoteParticipant(final RemoteParticipant remoteParticipant) {
        for (final RemoteDataTrackPublication remoteDataTrackPublication :
                remoteParticipant.getRemoteDataTracks()) {
            /*
             * Data track messages are received on the thread that calls setListener. Post the
             * invocation of setting the listener onto our dedicated data track message thread.
             */
            if (remoteDataTrackPublication.isTrackSubscribed()) {
                dataTrackMessageThreadHandler.post(() -> addRemoteDataTrack(remoteParticipant,
                        remoteDataTrackPublication.getRemoteDataTrack()));
            }
        }
    }

//    private void removeRemoteParticipant(RemoteParticipant remoteParticipant) {
//        // Clear the drawing of the remote participant
////        collaborativeDrawingView.clear(remoteParticipant);
//    }

    private void addRemoteDataTrack(RemoteParticipant remoteParticipant,
                                    RemoteDataTrack remoteDataTrack) {
//        dataTrackRemoteParticipantMap.put(remoteDataTrack, remoteParticipant);
        remoteDataTrack.setListener(remoteDataTrackListener());
    }

    private RemoteDataTrack.Listener remoteDataTrackListener() {
        return new RemoteDataTrack.Listener() {
            @Override
            public void onMessage(@NotNull RemoteDataTrack remoteDataTrack, @NotNull ByteBuffer byteBuffer) {

            }

            @Override
            public void onMessage(@NotNull RemoteDataTrack remoteDataTrack, @NotNull String message) {
                Timber.d("onMessage: %s", message);
                parserJson(message);
            }
        };
    }

    private void parserJson(String json) {
        try {
            JSONObject annotationWrapperJsonObject = new JSONObject(json);
            String annotationAction = annotationWrapperJsonObject.getString("type");

            switch (annotationAction) {
                case "ADD_ANNOTATION":
                    annotationView.addShape(annotationWrapperJsonObject.getJSONObject("annotation"),
                            annotationWrapperJsonObject.getJSONObject("roomDimensions"));
                    break;
                case "UPDATE_ANNOTATION":
                    annotationView.updateShape(annotationWrapperJsonObject.getJSONObject("annotation"),
                            annotationWrapperJsonObject.getJSONObject("roomDimensions"));
                    break;
                case "REMOVE_ANNOTATION":
                    annotationView.removeShape(annotationWrapperJsonObject.getJSONObject("annotation"));
                    break;
                case "RESTART_ANNOTATION":
                    annotationView.clearShapes();
                    break;
                case "UPDATE_SCREENSHOT":
                    annotationView.handleScreenshot(annotationWrapperJsonObject.getJSONObject("screenshot"), screenshotView);
                    break;
                case "ADMIN":
                    String identity = annotationWrapperJsonObject.getString("identity");
                    String action = annotationWrapperJsonObject.getString("action");
                    //  Timber.d("Identity: %s", room.getLocalParticipant().getIdentity());
                    LocalParticipant localParticipant = room.getLocalParticipant();
                    if(action.equals("broadcastSelectedParticipant")){
                        roomActivity.getRoomViewModel().getParticipantManager().changePinnedParticipant(identity);
                    } else if(identity.equals(localParticipant.getIdentity())){
                        switch (action){
                            case "mute":
                                this.roomActivity.runOnUiThread(this.binding.localAudio::performClick);
                                break;
                            case "disable":
                                this.roomActivity.runOnUiThread(this.binding.localVideo::performClick);
                                break;
                            case "kick":
                                this.roomActivity.runOnUiThread(this.binding.disconnectO::performClick);
                                break;
                            case "toggle_glasses":
                                this.roomActivity.runOnUiThread(this.orcana::showMenu);
                                break;
                        }
                    }
                    break;
            }
        } catch (JSONException e) {
            Timber.e(e);
        }
    }
}