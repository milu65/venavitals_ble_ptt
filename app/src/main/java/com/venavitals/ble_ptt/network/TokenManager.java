package com.venavitals.ble_ptt.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class TokenManager {
    private static TokenManager instance;
    private String token;
    private Context context;

    private TokenManager(Context context) {
        this.context = context.getApplicationContext(); // Use application context to avoid memory leaks
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context);
        }
        return instance;
    }

    public void saveToken(String token) {
        this.token = token;
        SharedPreferences sharedPreferences = context.getSharedPreferences("Tokens", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("JWT_TOKEN", token);
        editor.apply();
    }

    public String getToken() {
        if (token == null) {
            SharedPreferences sharedPreferences = context.getSharedPreferences("Tokens", Context.MODE_PRIVATE);
            token = sharedPreferences.getString("JWT_TOKEN", null);
        }
        return token;
    }

    public void clearToken() {
        this.token = null;
        SharedPreferences sharedPreferences = context.getSharedPreferences("Tokens", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("JWT_TOKEN");
        editor.apply();
    }

    public void isLoggedIn(LoginStatusListener listener) {
        String token = getToken();
        if (token == null) {
            Log.d("TokenManager", "No token available, user is not logged in.");
            listener.onStatusChecked(false);
        } else {
            Log.d("TokenManager", "Checking token validity.");
            checkTokenValidity(token, isValid -> {
                Log.d("TokenManager", "Token validity checked: " + isValid);
                listener.onStatusChecked(isValid);
            });
        }
    }

    private void checkTokenValidity(String token, TokenValidationCallback callback) {
        new Thread(() -> {
            String url = Constants.BASE_URL + "/api/validateToken";
            Log.d("TokenManager", "Sending token validation request.");
            NetworkUtils.sendGetRequestWithToken(url, token, new NetworkUtils.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    Log.d("TokenManager", "Token validation successful.");
                    callback.onTokenValidated(true);
                }
                @Override
                public void onFailure(int responseCode, String errorMessage) {
                    Log.d("TokenManager", "Token validation failed: " + errorMessage);
                    callback.onTokenValidated(false);
                    if (responseCode == 401) { // Unauthorized
                        Log.d("TokenManager", "Token is invalid or expired, clearing token.");
                        clearToken(); // Token is invalid or expired
                    }
                }
            });
        }).start();
    }


    public interface TokenValidationCallback {
        void onTokenValidated(boolean isValid);
    }

    public interface LoginStatusListener {
        void onStatusChecked(boolean isLoggedIn);
    }

}
