package com.venavitals.ble_ptt.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkUtils {
    public interface HttpCallback {
        void onSuccess(String response);
        void onFailure(int responseCode, String errorMessage);
    }

    public static void sendGetRequest(String url, HttpCallback callback) {
            HttpURLConnection connection = null;
            try {
                URL urlObj = new URL(url);
                connection = (HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    br.close();
                    if (callback != null) {
                        callback.onSuccess(response.toString());
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure(responseCode, "Failed to connect to the server. Response code: " + responseCode);
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailure(500, "An error occurred during the GET request: " + e.getMessage());
                }
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
    }

    public static void sendGetRequestWithToken(String url, String token, HttpCallback callback) {
        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + token); // Add the Bearer prefix here

            // Read the response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    callback.onSuccess(response.toString());
                }
            } else {
                callback.onFailure(responseCode, "Server responded with error: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure(-1, "Failed to connect: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }


    public static void sendPostRequest(String url, String jsonPayload, HttpCallback callback) {
        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    if (callback != null) {
                        callback.onSuccess(response.toString());
                    }
                }
            } else {
                if (callback != null) {
                    callback.onFailure(responseCode, "Failed to connect to the server. Response code: " + responseCode);
                }
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onFailure(HttpURLConnection.HTTP_INTERNAL_ERROR, "An error occurred during the POST request: " + e.getMessage());
            }
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static void sendPatchRequest(String url, String data, HttpCallback callback) {
        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("PATCH");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json"); // 设置内容类型为 JSON
            connection.setDoOutput(true); // 允许写入数据

            // 发送数据
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = data.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                br.close();
                if (callback != null) {
                    callback.onSuccess(response.toString());
                }
            } else {
                if (callback != null) {
                    callback.onFailure(responseCode, "Failed to connect to the server. Response code: " + responseCode);
                }
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onFailure(500, "An error occurred during the PATCH request: " + e.getMessage());
            }
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }




}
