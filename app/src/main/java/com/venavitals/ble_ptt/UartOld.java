
package com.venavitals.ble_ptt;


import static java.lang.Math.pow;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;


import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public class UartOld{
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private int mState = UART_PROFILE_DISCONNECTED;
    int lengthTxtValue = 16;
    byte[] txValue;
    double ch01Value = 0;
    double ch02Value = 0;

    int[] a = new int[lengthTxtValue];

    boolean isRunning = false;
    boolean isConnect = false;
    boolean isSaving = false;
    // Storage Permissions
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private final String deviceAddress = "D4:99:F5:24:E9:2D"; //TODO: constant

    private List<Double> samples= new ArrayList<>();

    private UartService mService;

    Consumer<Double> callback;

    public ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= $mService");
            if (mService != null) {
                if (!mService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
//                    finish() //TODO: exit here?
                }
            }

            Log.d(TAG,"${mService} try connect");

            mService.connect(deviceAddress);

            LocalBroadcastManager.getInstance(mService)
                    .registerReceiver(uartStatusChangeReceiver, makeGattUpdateIntentFilter());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisonnected mService= $mService");
            shutdown();
        }
    };

    private BroadcastReceiver uartStatusChangeReceiver = new BroadcastReceiver() {

            public void onReceive(Context context, final Intent intent) {
                String action = intent.getAction();

                if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                    Log.d(TAG, "UART_CONNECT_MSG");
                    isConnect = true;
                    mState = UART_PROFILE_CONNECTED;
                }

                if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                    Log.d(TAG, "UART_DISCONNECT_MSG");
                    mState = UART_PROFILE_DISCONNECTED;
                    mService.close();
                    isRunning = false;
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
                        samples.add(ch02Value);
                        callback.accept(ch02Value);
//                        ecgActivity.plotECG((float) ch02Value);
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



    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    public void setCallback(Consumer<Double> callback){
        this.callback=callback;
    }

    public void shutdown(){
        LocalBroadcastManager.getInstance(mService)
                .unregisterReceiver(uartStatusChangeReceiver);//TODO: non-tested
        mService.stopSelf();
        mService.disconnect();
        mService.close();
    }

    public void save(String path){
        String filename = "ecg_samples.txt";
        File dir = new File(path);
        if (!dir.exists()) {
            boolean dirCreated = dir.mkdirs();
            if (!dirCreated) {
                Log.e(TAG, "Failed to create directory: " + path);
                return;
            }
        }

        File file = new File(dir, filename);
        try {
            boolean fileCreated = file.createNewFile();
            if (!fileCreated && !file.exists()) {
                Log.e(TAG, "Failed to create file: " + file.getAbsolutePath());
                return;
            }

            Log.d(TAG, path + " " + filename);
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter osw = new OutputStreamWriter(fos);
                 BufferedWriter bw = new BufferedWriter(osw)) {
                for (Double item : samples) {
                    bw.write(String.valueOf(item));
                    bw.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}