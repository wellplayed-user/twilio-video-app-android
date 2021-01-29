package io.orcana;

import android.os.Handler;
import android.os.HandlerThread;
import android.widget.ImageView;

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

    ImageView screenshotView;
    AnnotationView annotationView;

    private Room room;
    private RoomManager roomManager;

    // Dedicated thread and handler for messages received from a RemoteDataTrack
    private final HandlerThread dataTrackMessageThread =
            new HandlerThread(DATA_TRACK_MESSAGE_THREAD_NAME);
    private final Handler dataTrackMessageThreadHandler;

    // Map used to map remote data tracks to remote participants
//    private final Map<RemoteDataTrack, RemoteParticipant> dataTrackRemoteParticipantMap =
//            new HashMap<>();

    public DataTrackLayer(RoomActivity roomActivity, RoomActivityBinding binding) {
        annotationView = binding.annotationView;
        screenshotView = binding.screenshotView;

        this.roomManager = roomActivity.getRoomManager();
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
                parserJson(message, annotationView, screenshotView);
            }
        };
    }

    public static void parserJson(String json, AnnotationView annotationView, ImageView screenShotView) {
        try {
            JSONObject annotationWrapperJsonObject = new JSONObject(json);
            String annotationAction = annotationWrapperJsonObject.getString("type");

            switch (annotationAction) {
                case "ADD_ANNOTATION":
                    JSONObject addAnnotationJsonObject = annotationWrapperJsonObject.getJSONObject("annotation");
                    annotationView.addShape(addAnnotationJsonObject);
                    break;
                case "UPDATE_ANNOTATION":
                    JSONObject updateAnnotationJsonObject = annotationWrapperJsonObject.getJSONObject("annotation");
                    annotationView.updateShape(updateAnnotationJsonObject);
                    break;
                case "REMOVE_ANNOTATION":
                    JSONObject removeAnnotationJsonObject = annotationWrapperJsonObject.getJSONObject("annotation");
                    annotationView.removeShape(removeAnnotationJsonObject);
                    break;
                case "RESTART_ANNOTATION":
                    annotationView.clearShapes();
                    break;
                case "UPDATE_SCREENSHOT":
                    JSONObject updateScreenshotJsonObject = annotationWrapperJsonObject.getJSONObject("screenshot");
                    annotationView.handleScreenshot(updateScreenshotJsonObject, screenShotView);
                    break;

            }
        } catch (JSONException e) {
            Timber.e(e);
        }
    }
}