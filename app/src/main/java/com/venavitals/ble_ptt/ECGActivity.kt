package com.venavitals.ble_ptt

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.androidplot.xy.BoundaryMode
import com.androidplot.xy.StepMode
import com.androidplot.xy.XYPlot
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarSensorSetting
import com.venavitals.ble_ptt.signal.Sample
import com.venavitals.ble_ptt.signal.Utils
import com.venavitals.ble_ptt.signal.filters.ButterworthBandpassFilter
import com.venavitals.ble_ptt.uart.UartOld
import com.venavitals.ble_ptt.uart.UartService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Date
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
    private lateinit var textViewPtt: TextView
    private lateinit var textViewInfo: TextView
    private lateinit var textViewSignalInfo: TextView
    private lateinit var ppgPlot: XYPlot
    private lateinit var ecgPlot: XYPlot
    private lateinit var ppgPlotter: EcgPlotter
    private lateinit var ecgPlotter: EcgPlotter
    private var ppgDisposable: Disposable? = null
    private var hrDisposable: Disposable? = null

    private lateinit var ppgDeviceId: String
    private var uart: UartOld =
        UartOld()

    private var ecgSamples: MutableList<Double> = Collections.synchronizedList(ArrayList()) //TODO: ConcurrentLinkedQueue might be better
    private var ppgSamples: MutableList<Sample> = Collections.synchronizedList(ArrayList())
    private var ppgFilteredSamples: ArrayList<Double> = ArrayList()
    private var ecgFilteredSamples: ArrayList<Double> = ArrayList()

    @Volatile private var startTimestamp: Long = 0
    @Volatile private var isSynchronized:Boolean =false

    private var ecgSR: Int = 250
    private var ppgSR: Int = 55  //28Hz, 44Hz, 55Hz, 135Hz, 176Hz

    private fun updateSignalInfo(text:String){
        runOnUiThread{
            textViewSignalInfo.text=text
        }
    }

    private fun updateInfo(text:String){
        runOnUiThread{
            textViewInfo.text=text
        }
    }

    private fun updatePtt(text:String){
        runOnUiThread{
            textViewPtt.text=text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ecg)
        // 尝试从 Intent 获取 deviceId
        ppgDeviceId = intent.getStringExtra("id").toString()
        Log.d(TAG, "ECGActivity received deviceId: $ppgDeviceId")

        if (ppgDeviceId.isNullOrEmpty()) {
            Toast.makeText(this, "No device ID provided. Please connect to a device first.", Toast.LENGTH_LONG).show()
            // 使用 Intent 显式地返回到 MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            // 结束当前活动并返回
            finish()
            return
        }
        textViewHR = findViewById(R.id.hr)
        textViewRR = findViewById(R.id.rr)
        textViewDeviceId = findViewById(R.id.deviceId)
        textViewBattery = findViewById(R.id.battery_level)
        textViewFwVersion = findViewById(R.id.fw_version)
        textViewPtt = findViewById(R.id.ptt)
        textViewInfo = findViewById(R.id.info)
        textViewSignalInfo = findViewById(R.id.sinfo)
        ppgPlot = findViewById(R.id.plot)
        ecgPlot = findViewById(R.id.ecg_plot)




        api = defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE
            )
        )
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BluetoothStateChanged $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connected " + polarDeviceInfo.deviceId)
//                Toast.makeText(applicationContext, R.string.connected, Toast.LENGTH_SHORT).show()
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
                textViewBattery.text=batteryLevelText
            }

        })
        try {
            api.connectToDevice(ppgDeviceId)
        } catch (a: PolarInvalidArgument) {
            a.printStackTrace()
        }

        uart.setCallback{plotECG(it)}

        bindService(
            Intent(this, UartService::class.java),
            uart.mServiceConnection,
            BIND_AUTO_CREATE
        ) // ServiceConnection.onServiceConnected() invoked after this

        val deviceIdText = "ID: $ppgDeviceId"
        textViewDeviceId.text = deviceIdText

        ppgPlotter = EcgPlotter("PPG", ppgSR)
        ppgPlotter.setListener(this)
        ppgPlot.addSeries(ppgPlotter.getSeries(), ppgPlotter.formatter)
        ppgPlot.setRangeBoundaries(160000, 200000, BoundaryMode.AUTO)
        ppgPlot.setRangeStep(StepMode.INCREMENT_BY_FIT, 20000.0)
        ppgPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 30000.0)
        ppgPlot.setDomainBoundaries(0, 20000, BoundaryMode.AUTO)
        ppgPlot.linesPerRangeLabel = 2
//        ppgPlot.graph.setMargins(-1000f,0f,0f,0f)


        ecgPlotter = EcgPlotter("ECG", ecgSR)
        ecgPlotter.setListener(this)
        ecgPlot.addSeries(ecgPlotter.getSeries(), ecgPlotter.formatter)
        ecgPlot.setRangeBoundaries(160000, 200000, BoundaryMode.AUTO)
        ecgPlot.setRangeStep(StepMode.INCREMENT_BY_FIT, 20000.0)
        ecgPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 30000.0)
        ecgPlot.setDomainBoundaries(0, 20000, BoundaryMode.AUTO)
        ecgPlot.linesPerRangeLabel = 2
//        ecgPlot.graph.setMargins(-1000f,0f,0f,0f)


//        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
//        bottomNavigationView.selectedItemId = R.id.navigation_chart
//        bottomNavigationView.setOnItemSelectedListener { item ->
//            when (item.itemId) {
//                R.id.navigation_connect -> {
//                    // Navigate to MainActivity
//                    startActivity(Intent(this, MainActivity::class.java))
//                    true
//                }
//                R.id.navigation_chart -> {
//                    // Stay in ECGActivity
//                    true
//                }
//                R.id.navigation_user -> {
//                    // Placeholder for future user activity
//                    true
//                }
//                R.id.navigation_settings -> {
//                    // Placeholder for future settings activity
//                    true
//                }
//                else -> false
//            }
//        }

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.selectedItemId = R.id.navigation_chart  // 设置选中的项为 chart

        val deviceId = intent.getStringExtra("id")

        bottomNavigationView.setOnItemSelectedListener { item ->
            NavigationHelper.handleNavigation(this, item.itemId, deviceId)
        }

    }

    public override fun onDestroy() {
        super.onDestroy()

        uart.shutdown()
        unbindService(uart.mServiceConnection)

        ppgDisposable?.let {
            if (!it.isDisposed) it.dispose()
        }
        api.shutDown()


        val path = getExternalFilesDir(null).toString();
//        path = Environment.getExternalStorageDirectory().toString();
        Log.d(TAG, "file save path: $path");
        val sdf = SimpleDateFormat("yyyy_MMdd_HH:mm:ss")
        val resultdate = Date(System.currentTimeMillis())
        Utils.saveValueSamples(ecgSamples,path,sdf.format(resultdate)+"_ecg_samples_"+ecgSR+".txt")
        Utils.saveSamples(ppgSamples,path,sdf.format(resultdate)+"_ppg_samples_"+ppgSR+".txt")
        Utils.saveValueSamples(ecgFilteredSamples,path,sdf.format(resultdate)+"_ecg_filtered_samples_"+ecgSR+".txt")
        Utils.saveValueSamples(ppgFilteredSamples,path,sdf.format(resultdate)+"_ppg_filtered_samples_"+ppgSR+".txt")
    }


    fun streamPPG() {
        val isDisposed = ppgDisposable?.isDisposed ?: true
        if (isDisposed) {
            val settingMap=HashMap<PolarSensorSetting.SettingType, Int>()
            settingMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = ppgSR
            settingMap[PolarSensorSetting.SettingType.RESOLUTION] = 22
            settingMap[PolarSensorSetting.SettingType.CHANNELS] = 4
            val setting = PolarSensorSetting(settingMap)

            ppgDisposable = api.startPpgStreaming(ppgDeviceId, setting).subscribe(
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

    private var ppgPlotterSize = ppgSR*5
    private var ecgPlotterSize = ecgSR*5


    private fun plotPPG(samples: List<PolarPpgData.PolarPpgSample>){
        //freeze state
        val ecgSize = ecgSamples.size
        val ppgSize = ppgSamples.size+samples.size
        val timestamp = System.currentTimeMillis()

        try {
            Log.d(TAG, "PPG data available ${samples.size} Thread:${Thread.currentThread()}")
            if (!isSynchronized) {
                isSynchronized = true
                startTimestamp = System.currentTimeMillis()
                runOnUiThread {
                    Toast.makeText(applicationContext, "Synchronized", Toast.LENGTH_SHORT).show()
                }

                for (data in samples) {
                    val value = data.channelSamples[0].toDouble()
                    ppgPlotter.sendSingleSampleWithoutUpdate(value)
                }
                ppgPlotter.update()
                return
            }
            for (data in samples) {
                val value = data.channelSamples[0].toDouble()
                val sample =
                    Sample(data.timeStamp, value)
                ppgSamples.add(sample)
            }

            //synchronized filtering and plotting
            if (ecgSize < ecgPlotterSize || ppgSize < ppgPlotterSize) {
                for (data in samples) {
                    val value = data.channelSamples[0].toDouble()
                    ppgPlotter.sendSingleSampleWithoutUpdate(value)
                }
                ppgPlotter.update()
                ecgPlotter.sendSamples(ecgSamples.toDoubleArray())
                return
            }
            val ecgLen = ecgSize / ecgSR.toDouble()
            val ppgLen = ppgSize / ppgSR.toDouble()
            val stopLen = Math.min(ppgLen, ecgLen)
            val ppgStopIdx = (ppgSR * stopLen).toInt() - 1
            val ecgStopIdx = (ecgSR * stopLen).toInt() - 1

            val ecgPast = DoubleArray(ecgPlotterSize)
            var idx = 0
            for (i in ecgStopIdx - ecgPlotterSize until ecgStopIdx) {
                ecgPast[idx++] = ecgSamples[i]
            }
            var ecgRes = ButterworthBandpassFilter.concatenate(ecgPast)
            ecgRes = ButterworthBandpassFilter.ecg250hzBandpassFilter(ecgRes)
            ecgRes = ButterworthBandpassFilter.trimSamples(ecgRes, ecgPlotterSize / 2)


            val ppgPast = DoubleArray(this.ppgPlotterSize)
            idx = 0
            for (i in ppgStopIdx - this.ppgPlotterSize until ppgStopIdx) {
                ppgPast[idx++] = ppgSamples[i].value
            }
            var ppgRes = ButterworthBandpassFilter.concatenate(ppgPast)
            ppgRes = ButterworthBandpassFilter.ppg55hzBandpassFilter(ppgRes)
            ppgRes = ButterworthBandpassFilter.trimSamples(ppgRes, this.ppgPlotterSize / 2)

            //plot filtered ppg and ecg
            ppgPlotter.sendSamples(ppgRes)
            ecgPlotter.sendSamples(ecgRes)

            //calc ptt
            val info= Utils.calcPTT(ecgRes,ppgRes)

            //update ptt
            if(info.PTT.toInt() !=0){
                updatePtt(String.format("%3.0f ms",info.PTT))
            }else{
                updatePtt("-")
            }

            //update signal info
            updateSignalInfo(String.format(
                "Peaks\nPPG Peaks: %d\t YRange[%f, %f]"+
                        "\nECG Peaks: %d\t YRange[%f, %f]",
                info.ppgPeaks,
                info.ppgMinValue,
                info.ppgMaxValue,
                info.ecgPeaks,
                info.ecgMinValue,
                info.ecgMaxValue
            ))

            //record filtered samples
            for(s in ecgRes){
                ecgFilteredSamples.add(s)
            }
            for(s in ppgRes){
                ppgFilteredSamples.add(s)
            }

            //prepare ppg time info
            var latestPPGTimestamp = ppgSamples.last().timestamp //Unit: us; Epoch time for polar is 2000-01-01T00:00:00Z
            latestPPGTimestamp /= 1000_000 // us to ms
            latestPPGTimestamp += 946684800000 // set epoch time to unit epoch
            val zonedPPGDateTime = Instant.ofEpochMilli(latestPPGTimestamp).atZone(ZoneId.systemDefault())
            val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

            //display info
            updateInfo(String.format(
                "Filtering...\nDuration: %d sec" +
                        "\nShort Period HR: %.2f"+
                        "\nAverage PPG Sample Rate: %.2f" +
                        "\nAverage ECG Sample Rate: %.2f" +
                        "\nECG-PPG Samples Length Diff: %.2f sec" +
                        "\nPPG latest sample datetime: %s",
                ((System.currentTimeMillis() - startTimestamp) / 1000),
                info.HR,
                ppgSize.toDouble() / ((timestamp - startTimestamp) / 1000),
                ecgSize.toDouble() / ((timestamp - startTimestamp) / 1000),
                (ecgLen - ppgLen),
                dateTimeFormatter.format(zonedPPGDateTime)
            ))
        }catch (e:ConcurrentModificationException){
            e.printStackTrace()
        }catch (e:ArrayIndexOutOfBoundsException){
            e.printStackTrace()
        }catch (e: RuntimeException){
            e.printStackTrace()
        }
    }


    private fun plotECG(num: Double) {
        if(isSynchronized){
            ecgSamples.add(num)
        }else{
            ecgPlotter.sendSingleSample(num)
        }
    }

    fun streamHR() {
        val isDisposed = hrDisposable?.isDisposed ?: true
        if (isDisposed) {
            hrDisposable = api.startHrStreaming(ppgDeviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { hrData: PolarHrData ->
                        for (sample in hrData.samples) {
//                            Log.d(TAG, "HR " + sample.hr)
                            if (sample.rrsMs.isNotEmpty()) {
                                val rrText = "(${sample.rrsMs.joinToString(separator = "ms, ")}ms)"
                                textViewRR.text = rrText
                            }

                            textViewHR.text = "${sample.hr} BPM"

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

//    override fun onResume() {
//        super.onResume()
//
//        // 设置导航栏的选中状态为chart
//        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
//        bottomNavigationView.selectedItemId = R.id.navigation_chart
//    }


}