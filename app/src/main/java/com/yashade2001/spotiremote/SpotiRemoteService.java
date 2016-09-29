package com.yashade2001.spotiremote;

import android.app.SearchManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
public class SpotiRemoteService extends Service {

    public static final String SERVER_URL = "http://192.168.1.31:1337";

    public SpotiRemoteService() { }

    private Socket mSocket;
    {
        try {
            mSocket = IO.socket(SERVER_URL);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    AudioManager audioManager;

    String trackName;
    String artistName;
    String albumName;
    String albumCoverUrl;

    BroadcastReceiver metaDataChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            postMetadata(intent.getStringExtra("id").replace("spotify:track:", ""));
        }
    };

    void postMetadata(String trackId) {
        Ion.with(getApplicationContext())
                .load("GET", "https://api.spotify.com/v1/tracks/" + trackId)
                .asString()
                .setCallback(new FutureCallback<String>() {
                    @Override
                    public void onCompleted(Exception e, String result) {
                        try {
                            JSONObject jsonObject = new JSONObject(result);
                            JSONObject album = jsonObject.getJSONObject("album");
                            JSONArray images = album.getJSONArray("images");
                            JSONObject image = new JSONObject(images.get(1).toString());
                            albumCoverUrl = image.getString("url");

                            albumName = album.getString("name");

                            JSONArray artists = jsonObject.getJSONArray("artists");
                            JSONObject firstArtist = new JSONObject(artists.get(0).toString());
                            artistName = firstArtist.getString("name");

                            trackName = jsonObject.getString("name");

                            Ion.with(getApplicationContext())
                                    .load("POST", SERVER_URL + "/api/metadata" +
                                            "?track=" + trackName.replace(" ", "%20") +
                                            "&artist=" + artistName.replace(" ", "%20") +
                                            "&album=" + albumName.replace(" ", "%20") +
                                            "&albumcover=" + albumCoverUrl)
                                    .asString();
                        } catch (JSONException e1) {
                            e1.printStackTrace();
                        }
                    }
                });
    }

    @Override
    public void onCreate() {
        Ion.getDefault(getApplicationContext()).configure().setLogging("ion", Log.DEBUG);

        mSocket.on("next", onNextEvent);
        mSocket.on("prev", onPrevEvent);
        mSocket.on("playpause", onPlayPauseEvent);
        mSocket.on("searchplay", onSearchplayEvent);
        mSocket.on("setvolume", onSetvolumeEvent);
        mSocket.on("share", onShareEvent);
        mSocket.connect();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.spotify.music.metadatachanged");
        registerReceiver(metaDataChangedReceiver, intentFilter);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    private Emitter.Listener onShareEvent = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];
            String track;
            try {
                track = data.getString("track");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(track));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private Emitter.Listener onSetvolumeEvent = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];
            int level;
            try {
                level = data.getInt("level");
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

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
        mSocket.off("setvolume", onSetvolumeEvent);
        mSocket.off("share", onShareEvent);
    }
}