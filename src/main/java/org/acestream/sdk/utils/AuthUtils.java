package org.acestream.sdk.utils;

import static org.acestream.sdk.Constants.USER_LEVEL_OPTION_NO_ADS;
import static org.acestream.sdk.Constants.USER_LEVEL_OPTION_PROXY_SERVER;

public class AuthUtils {
    public static boolean userLevelContainsOption(int level, int optionId) {
        return (level & optionId) != 0;
    }

    public static boolean hasProxyServer(int authLevel) {
        if(authLevel == -1) {
            // auth level is not initialized
            return false;
        }

        int[] options = {
                USER_LEVEL_OPTION_PROXY_SERVER,
        };

        for(int optionId: options) {
            if(userLevelContainsOption(authLevel, optionId)) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasNoAds(int authLevel) {
        if(authLevel == -1) {
            // auth level is not initialized
            return false;
        }

        int[] options = {
                USER_LEVEL_OPTION_NO_ADS,
        };

        for(int optionId: options) {
            if(userLevelContainsOption(authLevel, optionId)) {
                return true;
            }
        }

        return false;
    }
}
