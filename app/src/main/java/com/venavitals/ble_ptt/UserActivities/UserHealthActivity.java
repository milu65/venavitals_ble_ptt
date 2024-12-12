package com.venavitals.ble_ptt.UserActivities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.venavitals.ble_ptt.R;
import com.venavitals.ble_ptt.network.Constants;
import com.venavitals.ble_ptt.network.NetworkUtils;

import org.json.JSONObject;

import java.util.Arrays;

public class UserHealthActivity extends AppCompatActivity {
    private Long userId;
    private Spinner genderSpinner, bloodTypeSpinner;
    private EditText ageEditText, heightEditText, weightEditText;
    private Button actionButton;
    public enum Gender {
        MALE, FEMALE, OTHER;
    }
    public enum Blood_Type{
        A, B, AB, O, OTHER;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_userhealth);

        userId = getIntent().getLongExtra("USER_ID", -1);

        initView();
        populateSpinners();
        fetchUserHealth();
    }

    private void initView() {
        genderSpinner = findViewById(R.id.genderSpinner);
        bloodTypeSpinner = findViewById(R.id.bloodTypeSpinner);
        ageEditText = findViewById(R.id.ageEditText);
        heightEditText = findViewById(R.id.heightEditText);
        weightEditText = findViewById(R.id.WeightEditText);
        actionButton = findViewById(R.id.updateButton);

        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (actionButton.getText().equals("Create")) {
                    createUserHealth();
                } else {
                    updateUserHealth();
                }
            }
        });
    }

    private void fetchUserHealth() {
        String url = Constants.BASE_URL +"/api/userHealth/" + userId;
        Log.d("UserHealthActivity", url);
        new Thread(()->{
            NetworkUtils.sendGetRequest(url, new NetworkUtils.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    runOnUiThread(() -> {
                        Log.d("UserHealthActivity",response);
                        populateUserHealthData(response);
                    });
                }

                @Override
                public void onFailure(int responseCode, String errorMessage) {
                    runOnUiThread(() -> {
                        Toast.makeText(UserHealthActivity.this, "Failed to fetch user health: " + errorMessage, Toast.LENGTH_LONG).show();
                        actionButton.setText("Create");
                    });
                }
            });
        }).start();

    }

    private void populateUserHealthData(String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            // Assume the JSON object has these fields
            // Add methods to set spinner selection based on retrieved values
            ageEditText.setText(jsonObject.getString("age"));
            heightEditText.setText(jsonObject.getString("height"));
            weightEditText.setText(jsonObject.getString("weight"));
            actionButton.setText("Update");
        } catch (Exception e) {
            Toast.makeText(this, "Error parsing user health data", Toast.LENGTH_LONG).show();
            actionButton.setText("Create");
        }
    }

    private void populateSpinners() {
        // Populate Gender Spinner
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Arrays.stream(Gender.values()).map(Enum::name).toArray(String[]::new)
        );
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(genderAdapter);

        // Populate Blood Type Spinner
        ArrayAdapter<String> bloodTypeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Arrays.stream(Blood_Type.values()).map(Enum::name).toArray(String[]::new)
        );
        bloodTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bloodTypeSpinner.setAdapter(bloodTypeAdapter);
    }



    private void updateUserHealth() {
        String url =  Constants.BASE_URL + "/api/userHealth/" + userId;
        JSONObject postData = new JSONObject();
        try {
            postData.put("gender", genderSpinner.getSelectedItem().toString());
            postData.put("bloodType", bloodTypeSpinner.getSelectedItem().toString());
            postData.put("age", Integer.parseInt(ageEditText.getText().toString()));
            postData.put("height", Double.parseDouble(heightEditText.getText().toString()));
            postData.put("weight", Double.parseDouble(weightEditText.getText().toString()));

            NetworkUtils.sendPatchRequest(url, postData.toString(), new NetworkUtils.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    runOnUiThread(() -> Toast.makeText(UserHealthActivity.this, "User health updated successfully", Toast.LENGTH_LONG).show());
                }

                @Override
                public void onFailure(int responseCode, String errorMessage) {
                    runOnUiThread(() -> Toast.makeText(UserHealthActivity.this, "Failed to update user health: " + errorMessage, Toast.LENGTH_LONG).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error preparing update data", Toast.LENGTH_LONG).show();
        }
    }

    private void createUserHealth() {
        String url =  Constants.BASE_URL + "/api/userHealth";
        JSONObject postData = new JSONObject();
        try {
            postData.put("userId", userId);
            postData.put("gender", genderSpinner.getSelectedItem().toString());
            postData.put("bloodType", bloodTypeSpinner.getSelectedItem().toString());
            postData.put("age", Integer.parseInt(ageEditText.getText().toString()));
            postData.put("height", Double.parseDouble(heightEditText.getText().toString()));
            postData.put("weight", Double.parseDouble(weightEditText.getText().toString()));

            NetworkUtils.sendPatchRequest(url, postData.toString(), new NetworkUtils.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    runOnUiThread(() -> Toast.makeText(UserHealthActivity.this, "User health created successfully", Toast.LENGTH_LONG).show());
                }

                @Override
                public void onFailure(int responseCode, String errorMessage) {
                    runOnUiThread(() -> Toast.makeText(UserHealthActivity.this, "Failed to create user health: " + errorMessage, Toast.LENGTH_LONG).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error preparing create data", Toast.LENGTH_LONG).show();
        }
    }
}
