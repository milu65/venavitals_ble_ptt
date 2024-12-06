package com.venavitals.ble_ptt.UserActivities;

import android.os.Bundle;
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

import org.json.JSONObject;

public class UserProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_profile_activity);

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
                    runOnUiThread(() -> updateUserInfo(response));
                }
                @Override
                public void onFailure(int responseCode, String errorMessage) {
                    runOnUiThread(() -> Toast.makeText(UserProfileActivity.this,
                            "Error fetching user info: " + errorMessage, Toast.LENGTH_LONG).show());
                }
            });
        }).start();
    }


    private void updateUserInfo(String json) {
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

}
