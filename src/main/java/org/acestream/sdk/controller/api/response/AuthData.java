package org.acestream.sdk.controller.api.response;

import android.text.TextUtils;

import com.google.gson.Gson;

import org.acestream.sdk.controller.api.AuthCredentials;

import java.util.Locale;

import androidx.annotation.NonNull;

public class AuthData {
    public String method;
    public String token;
    public int auth_level;
    public int global_auth_level;
    public int session_auth_level;
    public String package_name = "?";
    public String package_color = "green";
    public int package_days_left;
    public int purse_amount;
    public int bonus_amount;
    public long bonuses_updated_at;
    public int got_error;
    public String auth_error;
    public String login;

    /**
     * Create empty (logged out) auth data
     *
     * @return AuthData
     */
    public static AuthData getEmpty() {
        AuthData authData = new AuthData();
        authData.method = "none";
        authData.token = null;
        authData.auth_level = 0;
        authData.package_name = "Basic";
        authData.package_color = "yellow";
        authData.package_days_left = -1;
        authData.purse_amount = -1;
        authData.bonus_amount = -1;
        authData.bonuses_updated_at = 0;
        authData.got_error = 0;
        authData.auth_error = null;

        return authData;
    }

    public AuthCredentials.AuthMethod getAuthMethod() {
        switch(method) {
            case "none":
                return AuthCredentials.AuthMethod.AUTH_NONE;
            case "acestream":
                return AuthCredentials.AuthMethod.AUTH_ACESTREAM;
            case "google":
                return AuthCredentials.AuthMethod.AUTH_GOOGLE;
            case "fb":
                return AuthCredentials.AuthMethod.AUTH_FACEBOOK;
            default:
                return AuthCredentials.AuthMethod.AUTH_NONE;
        }
    }

    public static AuthData fromJson(String json) {
        return new Gson().fromJson(json, AuthData.class);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public AuthData() {
    }

    public AuthData(@NonNull AuthData other) {
        method = other.method;
        token = other.token;
        auth_level = other.auth_level;
        package_name = other.package_name;
        package_color = other.package_color;
        package_days_left = other.package_days_left;
        purse_amount = other.purse_amount;
        bonus_amount = other.bonus_amount;
        got_error = other.got_error;
        auth_error = other.auth_error;
    }

    public String toString() {
        return String.format(Locale.ENGLISH, "<AuthData: method=%s token=%s level=%d(g=%d s=%d) package=%s color=%s left=%d purse=%d bonus=%d got_error=%d errmsg=%s>",
                method,
                token,
                auth_level,
                global_auth_level,
                session_auth_level,
                package_name,
                package_color,
                package_days_left,
                purse_amount,
                bonus_amount,
                got_error,
                auth_error);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuthData && equals((AuthData)other);
    }

    public boolean equals(AuthData other) {
        return other != null
                && TextUtils.equals(method, other.method)
                && TextUtils.equals(token, other.token);
    }

    public static boolean equals(AuthData a, AuthData b) {
        return a != null && a.equals(b);
    }
}
