package com.venavitals.ble_ptt.UserActivities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.venavitals.ble_ptt.NavigationHelper;
import com.venavitals.ble_ptt.R;
import com.venavitals.ble_ptt.network.Constants;
import com.venavitals.ble_ptt.network.NetworkUtils;
import com.venavitals.ble_ptt.network.TokenManager;

import org.json.JSONException;
import org.json.JSONObject;

public class UserProfileActivity extends AppCompatActivity {

    private Long userId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_profile_activity);

        userId = getIntent().getLongExtra("USER_ID", -1);
        Log.d("UserProfileActivity",userId.toString());
        // 配置AppBar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        // 处理导航图标点击事件
        toolbar.setNavigationOnClickListener(view -> {
            // 处理导航图标的点击事件
        });
        // 配置底部导航
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.navigation_user);  // 设置选中的项为 connect
        // 设置底部导航栏的项目选择监听器
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            // 使用 NavigationHelper 的单例调用 handleNavigation 方法
            return NavigationHelper.INSTANCE.handleNavigation(UserProfileActivity.this, item.getItemId());
        });

        fetchUserInfo();

        Button updateButton = findViewById(R.id.update_button);
        updateButton.setOnClickListener(view -> updateUserInfo());

    }

    private void fetchUserInfo() {
        String token = TokenManager.getInstance(this).getToken();
        if (token == null) {
            Toast.makeText(this, "No valid session found", Toast.LENGTH_LONG).show();
            return;
        }

        String url = Constants.BASE_URL + "/api/currentUser";

        new Thread(() -> {
            NetworkUtils.sendGetRequestWithToken(url, token, new NetworkUtils.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    runOnUiThread(() -> updateUserInfoForm(response));
                }
                @Override
                public void onFailure(int responseCode, String errorMessage) {
                    runOnUiThread(() -> Toast.makeText(UserProfileActivity.this,
                            "Error fetching user info: " + errorMessage, Toast.LENGTH_LONG).show());
                }
            });
        }).start();
    }


    private void updateUserInfoForm(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            String username = jsonObject.optString("username", "N/A");
            String email = jsonObject.optString("email", "N/A");
            String phone = jsonObject.optString("phone", "N/A");
            String address = jsonObject.optString("address", "N/A");

            TextView usernameView = findViewById(R.id.username);
            TextView emailView = findViewById(R.id.email);
            TextView phoneView = findViewById(R.id.phone);
            TextView addressView = findViewById(R.id.address);

            usernameView.setText(username);
            emailView.setText(email);
            phoneView.setText(phone);
            addressView.setText(address);
        } catch (Exception e) {
            Toast.makeText(this, "Error parsing user info", Toast.LENGTH_LONG).show();
        }
    }

    private void updateUserInfo() {
        EditText passwordView = findViewById(R.id.password);
        String password = passwordView.getText().toString().trim();

        // 检查密码是否为空
        if (password.isEmpty()) {
            Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // 收集其他用户信息
        String username = ((TextView) findViewById(R.id.username)).getText().toString();
        String email = ((TextView) findViewById(R.id.email)).getText().toString();
        String phone = ((TextView) findViewById(R.id.phone)).getText().toString();
        String address = ((TextView) findViewById(R.id.address)).getText().toString();

        // 构建要发送的JSON数据
        JSONObject userData = new JSONObject();
        try {
            userData.put("username", username);
            userData.put("password", password);
            userData.put("email", email);
            userData.put("phone", phone);
            userData.put("address", address);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 获取用户的Token
        String token = TokenManager.getInstance(this).getToken();
        String url = Constants.BASE_URL + "/api/users/"+ userId.toString();

        // 发送PATCH请求更新用户信息
        new Thread(() -> {
            NetworkUtils.sendPatchRequest(url, userData.toString(), new NetworkUtils.HttpCallback() {
                @Override
                public void onSuccess(String response) {
                    runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "User updated successfully", Toast.LENGTH_LONG).show());
                }

                @Override
                public void onFailure(int responseCode, String errorMessage) {
                    runOnUiThread(() -> Toast.makeText(UserProfileActivity.this, "Update failed: " + errorMessage, Toast.LENGTH_LONG).show());
                }
            });
        }).start();
    }


}
