
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
import java.util.List;
import java.util.function.Consumer;


public class UartOld{
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private int mState = UART_PROFILE_DISCONNECTED;
    int lengthTxtValue = 16;
    byte[] txValue;

    int[] a = new int[lengthTxtValue];

    boolean isRunning = false;
    boolean isConnect = false;
    boolean isSaving = false;
    // Storage Permissions
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

//    private final String deviceAddress = "D4:99:F5:24:E9:2D"; //TODO: constant
    private final String deviceAddress = "FC:A1:05:62:7A:F4";

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
    private int lastIdx =-1;
    private long lastTimestamp =-1;
    private long startTimestamp=-1;
    private long count=0;

    private int loop=0;

    private static long MAX_ECG_TIMESTAMP = 512000;

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

                    // 循环处理接收到的字节数组
                    for (i = 0; i < txValue.length; i++) {
                        // 将字节数组中的每个字节转为无符号整数存储在数组a中
                        a[i] = 0xFF & txValue[i];
                    }


                    /*
                    Device info 0-2 3 bytes
                    ECG C1 3-5 3 bytes
                    ECG C2 6-8 3 bytes
                    Timestamp 9-12 4 bytes // 32768 = 1 sec
                    Battery level 13 1 bytes
                    Idx 14 1 bytes
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
                    int ts =a[offsetTimestamp];
                    ts+=a[offsetTimestamp+1]<<8;
                    ts+=a[offsetTimestamp+2]<<16;
                    ts+=a[offsetTimestamp+3]<<24;
                    double t=(long)ts*1000/32768.0;
                    long timestamp = Math.round(t);
                    Sample sample=new Sample((long) loop *MAX_ECG_TIMESTAMP+timestamp,c1);

                    if(ts<0){
                        Log.e(TAG,"Wrong timestamp: "+timestamp);
                    }

                    // battery level
                    int offsetBattery = 13;
                    int batteryLevel=a[offsetBattery];
                    if(timestamp%(30*1000)<=4){
                        Log.i(TAG,"ECG batteryLevel: "+batteryLevel);
                    }

                    // idx
                    int offsetIdx = 14;
                    int idx=a[offsetIdx];

                    double SR;
                    count++;
                    if(startTimestamp==-1){
                        startTimestamp=System.currentTimeMillis();
                    }
                    SR= (double) count /(System.currentTimeMillis()-startTimestamp)*1000;
//                    Log.d(TAG,batteryLevel+"\t"+idx+"\t"+timestamp+"\t"+c1+"\t\tsr:"+SR);

                    if (lastIdx != -1) {
                        if ((lastIdx + 1) % 256 != idx) {
                            Log.d(TAG, "ECG Gap Found: " + lastIdx + " " + idx);
                        }
                    }
                    lastIdx =idx;

                    if(lastTimestamp != -1){
                        if(lastTimestamp>timestamp){
                            sample.timestamp+=MAX_ECG_TIMESTAMP;
                            loop++;
                        }
                    }
                    lastTimestamp=timestamp;

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