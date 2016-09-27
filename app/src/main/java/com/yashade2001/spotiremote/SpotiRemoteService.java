package com.yashade2001.spotiremote;

import android.app.SearchManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.provider.MediaStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
public class SpotiRemoteService extends Service {

    public SpotiRemoteService() { }

    private Socket mSocket;
    {
        try {
            mSocket = IO.socket("https://spotiremote.herokuapp.com");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        mSocket.on("next", onNextEvent);
        mSocket.on("prev", onPrevEvent);
        mSocket.on("playpause", onPlayPauseEvent);
        mSocket.on("searchplay", onSearchplayEvent);
        mSocket.connect();
    }

    private Emitter.Listener onNextEvent = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getApplicationContext().sendBroadcast(new Intent("com.spotify.mobile.android.ui.widget.NEXT"));
        }
    };

    private Emitter.Listener onPrevEvent = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getApplicationContext().sendBroadcast(new Intent("com.spotify.mobile.android.ui.widget.PREVIOUS"));
        }
    };

    private Emitter.Listener onPlayPauseEvent = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getApplicationContext().sendBroadcast(new Intent("com.spotify.mobile.android.ui.widget.PLAY"));
        }
    };

    private Emitter.Listener onSearchplayEvent = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];
            String query;
            try {
                query = data.getString("query");
                Intent intent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                intent.putExtra(SearchManager.QUERY, query);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off("next", onNextEvent);
        mSocket.off("prev", onPrevEvent);
        mSocket.off("playpause", onPlayPauseEvent);
        mSocket.off("searchplay", onSearchplayEvent);
    }
}