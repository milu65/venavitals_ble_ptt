package com.venavitals.ble_ptt.UserActivities;

import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.venavitals.ble_ptt.R;
import com.venavitals.ble_ptt.model.UserMeasurement;
import com.venavitals.ble_ptt.network.Constants;
import com.venavitals.ble_ptt.network.NetworkUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class HistoryDataActivity extends AppCompatActivity {

    private LinearLayout linearLayout;
    private Long userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_history_data);

        linearLayout = findViewById(R.id.linearLayoutHistoryData);
        userId = getIntent().getLongExtra("USER_ID", -1);
        Log.d("HistoryActivity", userId.toString());
        // Dummy data loading method
        fetchMeasurementData();
    }

    private void fetchMeasurementData() {

        String url = Constants.BASE_URL + "/api/measurement/" + userId;
        Log.d("fetchMeasurementData",url);
        new Thread(() -> {
            NetworkUtils.sendGetRequest(url, new NetworkUtils.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    runOnUiThread(() -> updateMeasurementData(response));
                }
                @Override
                public void onFailure(int responseCode, String errorMessage) {
                    runOnUiThread(() -> Toast.makeText(HistoryDataActivity.this,
                            "Error fetching measurement data: " + errorMessage, Toast.LENGTH_LONG).show());
                }
            });
        }).start();
    }

    private void updateMeasurementData(String jsonData) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        try {
            JSONArray jsonArray = new JSONArray(jsonData);
            List<UserMeasurement> measurements = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Log.d("fetchMeasurementData", jsonObject.toString());

                long measurementId = jsonObject.getLong("measurementId");
                long userId = jsonObject.getLong("userId");
                String sessionTimeString = jsonObject.getString("sessionTime");

                Log.d("fetchMeasurementData", "Measurement ID: " + measurementId + ", User ID: " + userId + ", Session Time: " + sessionTimeString);

                Date sessionTime = null;
                try {
                    sessionTime = dateFormat.parse(sessionTimeString);
                } catch (ParseException e) {
                    Log.e("fetchMeasurementData", "Date parsing failed for: " + sessionTimeString, e);
                }

                measurements.add(new UserMeasurement(measurementId, userId, sessionTime));
            }

            Log.d("fetchMeasurementData", "Displaying " + measurements.size() + " measurements");
            runOnUiThread(() -> displayMeasurements(measurements));

        } catch (JSONException e) {
            Log.e("fetchMeasurementData", "Error parsing JSON", e);
            runOnUiThread(() -> Toast.makeText(this, "Error parsing measurement data", Toast.LENGTH_LONG).show());
        }
    }


    private void displayMeasurements(List<UserMeasurement> measurements) {
        Log.d("displayMeasurements", "Running displayMeasurements");
        runOnUiThread(() -> {
            LinearLayout linearLayout = findViewById(R.id.linearLayoutHistoryData);
            if (linearLayout == null) {
                Log.e("displayMeasurements", "LinearLayout not found");
                return;
            }
            linearLayout.removeAllViews();  // Clear any existing views to refresh the list

            // 创建并设置标题 TextView
            TextView titleTextView = new TextView(this);
            titleTextView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            titleTextView.setText("My History Data");  // 设置标题文本
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);  // 设置文字大小
            titleTextView.setTextColor(ContextCompat.getColor(this, R.color.black));  // 设置文字颜色
            titleTextView.setPadding(20, 20, 20, 20);  // 设置内边距
            linearLayout.addView(titleTextView);  // 将标题 TextView 添加到 LinearLayout

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

            for (int i = 0; i < measurements.size(); i++) {
                UserMeasurement measurement = measurements.get(i);
                TextView textView = new TextView(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);

                // 为每一个除第一个外的项添加顶部边距
                if (i == 0) {
                    params.topMargin = 25;  // 为第一个测量数据项设置顶部边距
                }
                params.bottomMargin = 25;  // 为所有项设置底部边距

                textView.setLayoutParams(params);
                textView.setPadding(20, 20, 20, 20);
                try {
                    textView.setBackground(ContextCompat.getDrawable(this, R.drawable.rounded_background));
                    textView.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary));
                } catch (Exception e) {
                    Log.e("displayMeasurements", "Error setting background or text color", e);
                }
                textView.setTextSize(16);

                String sessionTimeStr = (measurement.getSessionTime() != null) ?
                        dateFormat.format(measurement.getSessionTime()) : "N/A";
                textView.setText(String.format(Locale.US, "Measurement ID: %d\nUser ID: %d\nSession Time: %s",
                        measurement.getMeasurementID(), measurement.getUserID(), sessionTimeStr));

                linearLayout.addView(textView);  // 将TextView添加到LinearLayout
            }
        });
    }




}





