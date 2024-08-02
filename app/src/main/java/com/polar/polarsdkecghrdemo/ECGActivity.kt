package com.polar.polarsdkecghrdemo

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.androidplot.xy.BoundaryMode
import com.androidplot.xy.StepMode
import com.androidplot.xy.XYPlot
import com.polar.polarsdkecghrdemo.UartService.LocalBinder
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.util.UUID


class ECGActivity : AppCompatActivity(), PlotterListener {
    companion object {
        private const val TAG = "ECGActivity"
    }

    private lateinit var api: PolarBleApi
    private lateinit var textViewHR: TextView
    private lateinit var textViewRR: TextView
    private lateinit var textViewDeviceId: TextView
    private lateinit var textViewBattery: TextView
    private lateinit var textViewFwVersion: TextView
    private lateinit var ppgPlot: XYPlot
    private lateinit var ecgPlot: XYPlot
    private lateinit var ppgPlotter: EcgPlotter
    private lateinit var ecgPlotter: EcgPlotter
    private var ppgDisposable: Disposable? = null
    private var hrDisposable: Disposable? = null



    private var me: ECGActivity = this

    private lateinit var ppgDeviceId: String
    private var mService: UartService? = null
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, rawBinder: IBinder) {
            mService = (rawBinder as LocalBinder).service
            val ms=mService
            Log.d(MainActivity.TAG, "onServiceConnected mService= $mService")
            if (ms != null) {
                if (!ms.initialize()) {
                    Log.e(MainActivity.TAG, "Unable to initialize Bluetooth")
                    finish()
                }
            }


            val deviceAddress = "D4:99:F5:24:E9:2D" //TODO: constant
            Log.d("UartService","${mService} try connect");

            val uartOld = UartOld(me,mService)
            mService?.connect(deviceAddress)

            LocalBroadcastManager.getInstance(me)
                .registerReceiver(UartOld.UARTStatusChangeReceiver, UartOld.makeGattUpdateIntentFilter())
        }

        override fun onServiceDisconnected(classname: ComponentName) {
            ////     mService.disconnect(mDevice);
            Log.d(MainActivity.TAG, "onServiceDisonnected mService= $mService")
            mService = null
        }
    }

    private fun serviceInit() {
        val bindIntent = Intent(this, UartService::class.java)
        bindService(bindIntent, mServiceConnection, BIND_AUTO_CREATE)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ecg)
        ppgDeviceId = intent.getStringExtra("id") ?: throw Exception("ECGActivity couldn't be created, no deviceId given")
        textViewHR = findViewById(R.id.hr)
        textViewRR = findViewById(R.id.rr)
        textViewDeviceId = findViewById(R.id.deviceId)
        textViewBattery = findViewById(R.id.battery_level)
        textViewFwVersion = findViewById(R.id.fw_version)
        ppgPlot = findViewById(R.id.plot)
        ecgPlot = findViewById(R.id.ecg_plot)

        api = defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BluetoothStateChanged $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connected " + polarDeviceInfo.deviceId)
                Toast.makeText(applicationContext, R.string.connected, Toast.LENGTH_SHORT).show()
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connecting ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device disconnected ${polarDeviceInfo.deviceId}")
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
                Log.d(TAG, "feature ready $feature")

                when (feature) {
                    PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {

                        streamPPG()
                        streamHR()
                    }
                    else -> {}
                }
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                if (uuid == UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")) {
                    val msg = "fm: " + value.trim { it <= ' ' }
                    Log.d(TAG, "Firmware: " + identifier + " " + value.trim { it <= ' ' })
                    textViewFwVersion.append(msg.trimIndent())
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "Battery level $identifier $level%")
                val batteryLevelText = "$level%"
                textViewBattery.append(batteryLevelText)
            }

        })
        try {
//            api.connectToDevice(ppgDeviceId)
        } catch (a: PolarInvalidArgument) {
            a.printStackTrace()
        }

        serviceInit()

        val deviceIdText = "ID: $ppgDeviceId"
        textViewDeviceId.text = deviceIdText

        ppgPlotter = EcgPlotter("PPG", 55)
        ppgPlotter.setListener(this)
        ppgPlot.addSeries(ppgPlotter.getSeries(), ppgPlotter.formatter)
        ppgPlot.setRangeBoundaries(160000, 200000, BoundaryMode.AUTO)
        ppgPlot.setRangeStep(StepMode.INCREMENT_BY_FIT, 2000.0)
        ppgPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 300.0)
        ppgPlot.setDomainBoundaries(0, 200, BoundaryMode.AUTO)
        ppgPlot.linesPerRangeLabel = 2


        ecgPlotter = EcgPlotter("ECG", 250)
        ecgPlotter.setListener(this)
        ecgPlot.addSeries(ecgPlotter.getSeries(), ecgPlotter.formatter)
        ecgPlot.setRangeBoundaries(160000, 200000, BoundaryMode.AUTO)
        ecgPlot.setRangeStep(StepMode.INCREMENT_BY_FIT, 2000.0)
        ecgPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 300.0)
        ecgPlot.setDomainBoundaries(0, 200, BoundaryMode.AUTO)
        ecgPlot.linesPerRangeLabel = 2
    }

    public override fun onDestroy() {
        super.onDestroy()

        unbindService(mServiceConnection);

        ppgDisposable?.let {
            if (!it.isDisposed) it.dispose()
        }
        api.shutDown()
    }


    fun streamPPG() {
        val isDisposed = ppgDisposable?.isDisposed ?: true
        if (isDisposed) {
            ppgDisposable =
                api.requestStreamSettings(ppgDeviceId, PolarBleApi.PolarDeviceDataType.PPG)
                    .toFlowable() //TODO: ??
                    .flatMap { settings: PolarSensorSetting ->  api.startPpgStreaming(ppgDeviceId, settings)}
                    .subscribe(
                        { polarPpgData: PolarPpgData ->
                            if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                                plotPPG(polarPpgData.samples)
                            }
                        },
                        { error: Throwable ->
                            Log.e(TAG, "PPG stream failed. Reason $error")
                        },
                        { Log.d(TAG, "PPG stream complete") }
                    )
        } else {
            // NOTE stops streaming if it is "running"
            ppgDisposable?.dispose()
            ppgDisposable = null
        }
    }

    fun plotPPG(samples: List<PolarPpgData.PolarPpgSample>){
        Log.d(TAG, "PPG data available ${samples.size} Thread:${Thread.currentThread()}")
        for (data in samples) {
            //Log.d(TAG, "PPG data available    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}")
            ppgPlotter.sendSingleSampleWithoutUpdate(data.channelSamples[0].toFloat())
        }
        ppgPlotter.update()
    }


    fun plotECG(num: Float) {
        ecgPlotter.sendSingleSample(num)

    }

    fun streamHR() {
        val isDisposed = hrDisposable?.isDisposed ?: true
        if (isDisposed) {
            hrDisposable = api.startHrStreaming(ppgDeviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { hrData: PolarHrData ->
                        for (sample in hrData.samples) {
                            Log.d(TAG, "HR " + sample.hr)
                            if (sample.rrsMs.isNotEmpty()) {
                                val rrText = "(${sample.rrsMs.joinToString(separator = "ms, ")}ms)"
                                textViewRR.text = rrText
                            }

                            textViewHR.text = sample.hr.toString()

                        }
                    },
                    { error: Throwable ->
                        Log.e(TAG, "HR stream failed. Reason $error")
                        hrDisposable = null
                    },
                    { Log.d(TAG, "HR stream complete") }
                )
        } else {
            // NOTE stops streaming if it is "running"
            hrDisposable?.dispose()
            hrDisposable = null
        }
    }

    override fun update() {
        runOnUiThread {
            ppgPlot.redraw()
        }
        runOnUiThread {
            ecgPlot.redraw()
        }

    }

}