package com.venavitals.ble_ptt.service;

import android.util.Log;

import com.venavitals.ble_ptt.network.Constants;
import com.venavitals.ble_ptt.network.NetworkUtils;

public class MeasurementDataService {
    private static final String BASE_URL = Constants.BASE_URL+"/api/";

    public void saveMeasurementData(String jsonPayload) {
//        String jsonPayload = JsonUtil.toJson(data);
        String url = BASE_URL + "measurement";

        // 使用新线程来处理网络请求，避免阻塞主线程
        new Thread(() -> {
            try {
                NetworkUtils.sendPostRequest(url, jsonPayload,null);
            } catch (Exception e) {
                // Log and handle exceptions
                Log.e("DataService", "Error sending measurement data", e);
            }
        }).start();
    }
}
