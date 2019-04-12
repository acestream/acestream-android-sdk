package org.acestream.sdk.controller.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.acestream.sdk.controller.api.response.VastTag;

/**
 * Helper class for VastTag serialization
 */
public class VastTags {
    public static String toJson(VastTag[] tags) {
        if(tags == null) {
            return null;
        }
        return new Gson().toJson(tags);
    }

    public static VastTag[] fromJson(String data) {
        if(data == null) {
            return null;
        }
        return new Gson().fromJson(
                data,
                new TypeToken<VastTag[]>(){}.getType()
        );
    }
}
