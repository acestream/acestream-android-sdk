package org.acestream.sdk.controller.api.response;

import java.util.List;
import java.util.Map;

public class AdConfig {
    public int max_ads;
    public int ima_sdk_handler_delay;
    public Map<String,Integer> load_timeout;
    public Map<String,Integer> min_impression_interval;
    public Map<String, List<List<String>>> priorities;
    public List<String> custom_ads_rv_providers;
    public List<String> providers;
    public List<String> appodeal_disable_networks;
    public Map<Integer,String> admob_rewarded_video_segments;
    public int admob_rewarded_video_default_segment;
    public int admob_rewarded_video_auto_segment;
    public int admob_interstitial_background_load_interval;
    public boolean show_bonus_ads_activity;

    public boolean isProviderEnabled(String name) {
        return providers != null && providers.contains(name);
    }
}
