package org.acestream.sdk.controller.api;

public class AuthCredentials {

    public enum AuthMethod {
        AUTH_NONE,
        AUTH_ACESTREAM,
        AUTH_GOOGLE,
        AUTH_FACEBOOK,
    }

    private AuthMethod mType;
    private String mToken;
    private String mLogin;
    private String mPassword;

    private AuthCredentials(AuthMethod type) {
        mType = type;
    }

    public AuthMethod getType() {
        return mType;
    }

    public String getTypeString() {
        switch(mType) {
            case AUTH_NONE:
                return "none";
            case AUTH_ACESTREAM:
                return "acestream";
            case AUTH_GOOGLE:
                return "google";
            case AUTH_FACEBOOK:
                return "fb";
            default:
                return "unknown";
        }
    }

    public String getToken() {
        return mToken;
    }

    public String getLogin() {
        return mLogin;
    }

    public String getPassword() {
        return mPassword;
    }

    public static final class Builder {
        private AuthCredentials mCredentials;
        public Builder(AuthMethod type) {
            mCredentials = new AuthCredentials(type);
        }

        public void setToken(String token) {
            mCredentials.mToken = token;
        }

        public void setLogin(String login) {
            mCredentials.mLogin = login;
        }

        public void setPassword(String password) {
            mCredentials.mPassword = password;
        }

        public AuthCredentials build() {
            return mCredentials;
        }
    }
}
