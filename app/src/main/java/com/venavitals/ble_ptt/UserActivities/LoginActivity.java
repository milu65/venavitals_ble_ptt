package com.venavitals.ble_ptt.UserActivities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.venavitals.ble_ptt.R;
import com.venavitals.ble_ptt.network.TokenManager;
import com.venavitals.ble_ptt.service.LoginService;

public class LoginActivity extends AppCompatActivity {

    private LoginService loginService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        EditText usernameEditText = findViewById(R.id.username);
        EditText passwordEditText = findViewById(R.id.password);
        Button loginButton = findViewById(R.id.login_button);

        loginService = new LoginService(this, new LoginService.LoginResultListener() {
            @Override
            public void onLoginSuccess(String jwt) {
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_LONG).show();
                    TokenManager.getInstance(LoginActivity.this).saveToken(jwt);  // Save JWT for future use

                    // Since user details are not returned, start UserInfoActivity which should fetch user details independently
                    Intent intent = new Intent(LoginActivity.this, UserInfoActivity.class);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onLoginFailure(String errorMessage) {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Failed to login: " + errorMessage, Toast.LENGTH_LONG).show());
            }
        });

        loginButton.setOnClickListener(view -> {
            String username = usernameEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            if (!username.isEmpty() && !password.isEmpty()) {
                loginService.performLogin(username, password);
            } else {
                Toast.makeText(this, "Username and password cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
    }
}




//
//public class LoginActivity extends AppCompatActivity {
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.login_activity);
//
//        final EditText usernameEditText = findViewById(R.id.username);
//        final EditText passwordEditText = findViewById(R.id.password);
//        Button loginButton = findViewById(R.id.login_button);
//
//        loginButton.setOnClickListener(view -> {
//            String username = usernameEditText.getText().toString();
//            String password = passwordEditText.getText().toString();
//
//            if (!username.isEmpty() && !password.isEmpty()) {
//                performLogin(username, password);
//            } else {
//                Toast.makeText(LoginActivity.this, "Username and password cannot be empty", Toast.LENGTH_SHORT).show();
//            }
//        });
//    }
//
//    private void performLogin(String username, String password) {
//        try {
//            JSONObject jsonPayload = new JSONObject();
//            jsonPayload.put("username", username);
//            jsonPayload.put("password", password);
//            String jsonString = jsonPayload.toString();
//
//            // 使用回调接口
//            new Thread(() -> {
//                NetworkUtils.sendPostRequest("http://172.20.10.4:8080/api/login", jsonString, new NetworkUtils.HttpCallback() {
//                    @Override
//                    public void onSuccess(String response) {
//                        Log.d("LoginActivity", "Response received: " + response);
//                        final String userId = response;  // 直接使用返回的响应作为用户ID
//
//                        runOnUiThread(() -> {
//                            Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_LONG).show();
//                            // 启动 UserInfoActivity 并传递用户ID
//                            Intent intent = new Intent(LoginActivity.this, UserInfoActivity.class);
//                            intent.putExtra("userId", userId);
//                            startActivity(intent);
//                            finish(); // 结束当前的 LoginActivity
//                        });
//                    }
//
//                    @Override
//                    public void onFailure(int responseCode, String errorMessage) {
//                        runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Failed to login: " + errorMessage, Toast.LENGTH_LONG).show());
//                        // 处理失败逻辑，显示错误信息等
//                    }
//                });
//            }).start();
//
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//    }
//}
