package com.venavitals.ble_ptt.UserActivities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.venavitals.ble_ptt.UserActivities.LoginActivity;
import com.venavitals.ble_ptt.NavigationHelper;
import com.venavitals.ble_ptt.R;
import com.venavitals.ble_ptt.network.Constants;
import com.venavitals.ble_ptt.network.NetworkUtils;
import com.venavitals.ble_ptt.network.TokenManager;

import org.json.JSONObject;

public class UserInfoActivity extends AppCompatActivity {
    private TextView usernameView;
    private ListView userOptions;
    private Button logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_info_activity);

        usernameView = findViewById(R.id.username);
        userOptions = findViewById(R.id.userOptions);
        logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(UserInfoActivity.this)
                        .setTitle("Confirm Logout")  // 设置对话框的标题
                        .setMessage("Are you sure you want to logout?")  // 设置对话框显示的消息
                        .setPositiveButton("Logout", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                performLogout();  // 调用注销方法
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();  // 取消操作，关闭对话框
                            }
                        })
                        .show();  // 显示对话框
            }
        });


        // Setup options list
        String[] options = {"User Profile", "User Health Profile", "History Data"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_user_option, R.id.textViewOption, options);
        userOptions.setAdapter(adapter);
        userOptions.setOnItemClickListener((parent, view, position, id) -> handleOptionClick(position));
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
            return NavigationHelper.INSTANCE.handleNavigation(UserInfoActivity.this, item.getItemId());
        });

        fetchUserInfo();



    }

    private void handleOptionClick(int position) {
        switch (position) {
            case 0:
                startActivity(new Intent(this, UserProfileActivity.class));
                break;
            case 1:
                startActivity(new Intent(this, UserHealthActivity.class));
                break;
            case 2:
                startActivity(new Intent(this, HistoryData.class));
                break;
        }
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
                    runOnUiThread(() -> updateUserName(response));
                }
                @Override
                public void onFailure(int responseCode, String errorMessage) {
                    runOnUiThread(() -> Toast.makeText(UserInfoActivity.this,
                            "Error fetching user info: " + errorMessage, Toast.LENGTH_LONG).show());
                }
            });
        }).start();
    }


    private void updateUserName(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            String username = jsonObject.optString("username", "N/A");

            TextView usernameView = findViewById(R.id.username);

            usernameView.setText("User: " + username);
        } catch (Exception e) {
            Toast.makeText(this, "Error parsing user info", Toast.LENGTH_LONG).show();
        }
    }

    private void performLogout() {
        // 清除JWT
        TokenManager.getInstance(this).clearToken();

        // 跳转到登录界面
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        // 可选：发送注销请求到服务器
        // sendLogoutRequestToServer();
    }



}
