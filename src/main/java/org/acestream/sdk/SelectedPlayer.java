package org.acestream.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class SelectedPlayer {
    private final static String TAG = "AS/SelectedPlayer";

    public static final int LOCAL_PLAYER = 0;
    public static final int CONNECTABLE_DEVICE = 1;
    public static final int ACESTREAM_DEVICE = 2;
    public static final int OUR_PLAYER = 3;

    public int type;
    public String id1;
    public String id2;
    private String name;

    public static SelectedPlayer getOurPlayer() {
        SelectedPlayer player = new SelectedPlayer();
        player.type = OUR_PLAYER;
        player.name = Constants.OUR_PLAYER_NAME;
        return player;
    }

    public SelectedPlayer() {
    }

    public SelectedPlayer(int type) {
        this.type = type;
    }

    public SelectedPlayer(int type, String id1, String id2) {
        this.type = type;
        this.id1 = id1;
        this.id2 = id2;
    }

    public boolean isOurPlayer() {
        return this.type == OUR_PLAYER;
    }

    public boolean isRemote() {
        return this.type == ACESTREAM_DEVICE || this.type == CONNECTABLE_DEVICE;
    }

    public boolean canResume() {
        return this.type == SelectedPlayer.ACESTREAM_DEVICE || this.type == SelectedPlayer.CONNECTABLE_DEVICE;
    }

    public static String toJson(@Nullable SelectedPlayer player) {
        return (player == null) ? null : player.toJson();
    }

    public String toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("type", type);
            root.put("id1", id1);
            root.put("id2", id2);
            root.put("name", name);
            return root.toString();
        }
        catch(JSONException e) {
            throw new IllegalStateException("failed to serialize selected player", e);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "SelectedPlayer(type=%d id1=%s id2=%s name=%s)",
                type,
                id1,
                id2,
                name);
    }

    public boolean equals(SelectedPlayer other) {
        if(other == null) {
            return false;
        }
        else if(other.type != this.type) {
            return false;
        }
        else if(!TextUtils.equals(other.id1, this.id1)) {
            return false;
        }
        return true;
    }

    public static boolean equals(SelectedPlayer a, SelectedPlayer b) {
        return a != null && a.equals(b);
    }

    public static SelectedPlayer fromJson(@Nullable String jsonString) throws JSONException {
        if(jsonString == null)
            return null;

        SelectedPlayer player = new SelectedPlayer();
        JSONObject root = new JSONObject(jsonString);
        player.type = root.getInt("type");
        player.id1 = root.optString("id1");
        player.id2 = root.optString("id2");
        player.name = root.optString("name");
        return player;
    }

    public static SelectedPlayer fromDevice(BaseRemoteDevice device) {
        SelectedPlayer player = new SelectedPlayer();
        player.type = device.isAceCast() ? ACESTREAM_DEVICE : CONNECTABLE_DEVICE;
        player.id1 = device.getId();
        player.id2 = device.getName();
        return player;
    }

    public static SelectedPlayer fromIntentExtra(@NonNull Intent intent) {
        try {
            return fromJson(intent.getStringExtra(Constants.EXTRA_SELECTED_PLAYER));
        }
        catch(JSONException e) {
            throw new IllegalStateException("failed to deserialize selected player from intent extra", e);
        }
    }

    public static SelectedPlayer fromResolveInfo(Context context, ResolveInfo ri) {
        SelectedPlayer player = new SelectedPlayer();
        player.type = LOCAL_PLAYER;
        player.id1 = ri.activityInfo.packageName;
        player.id2 = ri.activityInfo.name;
        player.name = ri.loadLabel(context.getPackageManager()).toString();
        return player;
    }

    public String getName() {
        if(this.type == CONNECTABLE_DEVICE || this.type == ACESTREAM_DEVICE) {
            return this.id2;
        }
        else {
            return this.name;
        }
    }

    public String getId() {
        if(this.type == OUR_PLAYER) {
            return Constants.OUR_PLAYER_ID;
        }
        else if(this.type == CONNECTABLE_DEVICE) {
            return "chromecast:" + this.id1 + ":" + this.id2;
        }
        else if(this.type == ACESTREAM_DEVICE) {
            return "acecast:" + this.id1 + ":" + this.id2;
        }
        else {
            return this.id1 + ":" + this.id2;
        }
    }

    public static SelectedPlayer fromId(@Nullable  String id) {
        if(TextUtils.isEmpty(id)) {
            return null;
        }

        if(TextUtils.equals(id, Constants.OUR_PLAYER_ID)) {
            return getOurPlayer();
        }
        else {
            SelectedPlayer player = new SelectedPlayer();
            player.type = LOCAL_PLAYER;

            String[] parts = id.split(":");
            if (parts.length != 2) {
                Log.e(TAG, "fromId: malformed player id: " + id);
                return null;
            }
            player.id1 = parts[0];
            player.id2 = parts[1];
            return player;
        }
    }
}
