package com.venavitals.ble_ptt.service;

import android.content.Context;

import com.venavitals.ble_ptt.network.Constants;
import com.venavitals.ble_ptt.network.NetworkUtils;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginService {
    private Context context;
    private LoginResultListener listener;

    public interface LoginResultListener {
        void onLoginSuccess(String jwt);
        void onLoginFailure(String errorMessage);
    }

    public LoginService(Context context, LoginResultListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void performLogin(String username, String password) {
        try {
            JSONObject jsonPayload = new JSONObject();
            jsonPayload.put("username", username);
            jsonPayload.put("password", password);
            String jsonString = jsonPayload.toString();

            new Thread(() -> {
                NetworkUtils.sendPostRequest(Constants.BASE_URL + "/api/login", jsonString, new NetworkUtils.HttpCallback() {
                    @Override
                    public void onSuccess(String response) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            String jwt = jsonResponse.optString("token");  // 提取 JWT
                            if (!jwt.isEmpty()) {
                                listener.onLoginSuccess(jwt);
                            } else {
                                listener.onLoginFailure("JWT not found in response");
                            }
                        } catch (JSONException e) {
                            listener.onLoginFailure("Error parsing response: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }


                    @Override
                    public void onFailure(int responseCode, String errorMessage) {
                        listener.onLoginFailure(errorMessage);
                    }
                });
            }).start();
        } catch (JSONException e) {
            listener.onLoginFailure("Error processing JSON");
            e.printStackTrace();
        }
    }
}





