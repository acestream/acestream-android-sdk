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
}
