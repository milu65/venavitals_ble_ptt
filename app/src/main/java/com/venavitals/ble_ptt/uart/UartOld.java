
package com.venavitals.ble_ptt.uart;


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

import com.venavitals.ble_ptt.signal.Sample;

import java.util.ArrayList;
import java.util.Arrays;
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

    Consumer<Sample> callback;

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
//                    Log.d(TAG,"ECG data available");
                    // get bytes data from intent
                    txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
//                    System.out.println(Arrays.toString(txValue));

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


                    /*
                    Device info 3 bytes
                    ECG C1 3 bytes
                    ECG C2 3 bytes
                    Timestamp 4 bytes // 32768 = 1 sec
                    Battery level 1 bytes
                    Idx 1 bytes
                     */

                    // ch1
                    int offsetCh1 = 3;
                    double c1 = 65536 * a[offsetCh1] + 256 * a[offsetCh1 + 1] + a[offsetCh1 + 2];
                    if (c1 >= 8388608) {// 如果数值是负数，则转换为补码
                        c1 = c1 - 16777216;
                    }
                    c1 = c1 * 2.5 / pow(2, 23);

                    // timestamp
                    int offsetTimestamp = 9;
                    int ts =a[offsetTimestamp]+a[offsetTimestamp+1]<<8+a[offsetTimestamp+2]<<16+a[offsetTimestamp+3]<<24;
                    Sample sample=new Sample((long) ts,c1);
                    callback.accept(sample);
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

    public void setCallback(Consumer<Sample> callback){
        this.callback=callback;
    }

    public void shutdown(){
        LocalBroadcastManager.getInstance(mService)
                .unregisterReceiver(uartStatusChangeReceiver);//TODO: non-tested
        mService.stopSelf();
        mService.disconnect();
        mService.close();
    }
}