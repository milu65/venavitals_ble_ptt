
package com.polar.polarsdkecghrdemo;


import static java.lang.Math.pow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;


import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;


public class UartOld extends Activity{
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_SELECT_DEVICE2 = 3;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;
    private static final int CREATE_REQUEST_CODE = 40;
    private static final int OPEN_REQUEST_CODE = 41;
    private static final int SAVE_REQUEST_CODE = 42;
    public static final int request_code = 10;
    //private static EditText textView;
    private int mState = UART_PROFILE_DISCONNECTED;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private Button btnConnectDisconnect;
    private Button btnConnectDisconnect2;
    int lengthTxtValue = 16;
    double[] ECG_dataIn;
    double[] outPut;
    byte[] txValue;
    double miny = -2;
    String dataSaved = "";
    double ch01Value = 0;
    double ch02Value = 0;
    double ch03Value = 0;
    double ch04Value = 0;

    int[] a = new int[lengthTxtValue];

    private int lastX1 = 0;
    private int lastX2 = 0;
    private int lastX3 = 0;
    private int lastX4 = 0;
    boolean isRunning = false;
    boolean isConnect = false;
    boolean isSaving = false;
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    ECGActivity ecgActivity;

    public static BroadcastReceiver UARTStatusChangeReceiver;



    public UartOld(ECGActivity ecgActivity,UartService mService) {

        this.ecgActivity=ecgActivity;

        UARTStatusChangeReceiver = new BroadcastReceiver() {

            public void onReceive(Context context, final Intent intent) {
                String action = intent.getAction();

                final Intent mIntent = intent;
                if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "UART_CONNECT_MSG");
                        isConnect = true;
                        mState = UART_PROFILE_CONNECTED;
                    });
                }

                if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                        isRunning = false;

                    });
                }


                if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                    mService.enableTXNotification();
                }
                int i;

                if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {// 如果动作是数据可用的广播动作
                    Log.d(TAG,"ECG data available");
                    // 从意图中获取蓝牙传输的字节数据
                    txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);

                    // 循环处理接收到的字节数组
                    for (i = 0; i < txValue.length; i++) {
                        // 将字节数组中的每个字节转为无符号整数存储在数组a中
                        a[i] = 0xFF & txValue[i];

                        // 如果是最后一个字节
                        if (i == txValue.length - 1) {
                            // 记录ch01通道的值
                            ch01Value = a[i];
                            // 将数据点添加到ch01通道的图表数据系列中
//                        line_series_02.appendData(new DataPoint(lastX1++, a[i]), true, 400);
                        }
                    }

                    // 处理字节数组中的数据，解析为ECG信号
                    for (i = 1; i < (txValue.length - 1) / 3; i++) {
                        double temp_value = 0;
                        // 将三个字节的数据组合成一个24位的整数
                        temp_value = 65536 * a[3 * i] + 256 * a[3 * i + 1] + a[(3 * i) + 2];

                        // 如果数值是负数，则转换为补码
                        if (temp_value >= 8388608) {
                            temp_value = temp_value - 16777216;
                        }

                        if (i%2==1) {// 隔一个取一个
                            ch02Value = temp_value * 2.5 / pow(2, 23);
                            ecgActivity.plotECG((float) ch02Value);
//                        line_series_03.appendData(new DataPoint(lastX2++, temp_value * 4.5 / (pow(2, 23) - 1)), true, 400);
                        }

//                    // 根据不同的通道，将数据值处理并添加到相应的图表数据系列中
//                    if (i == 1) {
//                        ch02Value = temp_value * 2.5 / pow(2, 23);
//                        line_series_03.appendData(new DataPoint(lastX2++, temp_value * 4.5 / (pow(2, 23) - 1)), true, 400);
//                    } else if (i == 2) {
//                        ch03Value = temp_value * 2.5 / pow(2, 23);
//                        line_series_04.appendData(new DataPoint(lastX3++, temp_value * 4.5 / (pow(2, 23) - 1)), true, 400);
//                    }
                    }

                }


                if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                    if(!isSaving) {
                        Log.i(TAG,"Device doesn't support UART. Disconnecting");
                        mService.disconnect();
                    }
                }
            }
        };

    }


    public static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }


}