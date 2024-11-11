package com.venavitals.ble_ptt

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
import java.util.Collections
import java.util.Date
import java.util.UUID


private const val MStoNS = 1000_000

private const val PolarEpochTime = 946684800000

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

    private var ecgSamples: MutableList<Sample> = Collections.synchronizedList(ArrayList()) //TODO: ConcurrentLinkedQueue might be better
    private var ppgSamples: ArrayList<Sample> = ArrayList()
    private var ppgFilteredSamples: ArrayList<Double> = ArrayList()
    private var ecgFilteredSamples: ArrayList<Double> = ArrayList()
    private var hrSamples: ArrayList<Sample> = ArrayList()
    private var pttSamples: ArrayList<Sample> = ArrayList()

    @Volatile private var startTimestamp: Long = 0
    @Volatile private var ppgReceived:Boolean =false
    private var ecgFirstSampleTimestamp = 0L
    private val ecgFirstSampleTimestampOffset: Long = 20L
    private var ppgFirstSampleTimestamp: Long = 0L
    private val ppgFirstSampleTimestampOffset: Long = 250L
    private var ppgAdjustedFirstSampleTimestamp: Long = 0L

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
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
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
//                    PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP-> {
//                        val calendar: Calendar = Calendar.getInstance().apply {
//                            timeInMillis = System.currentTimeMillis()+2050
//                        }
//                        api.setLocalTime(ppgDeviceId,calendar).subscribe({
//                            println("time set")
//                        })
//                        while(true){
//                            Thread.sleep(1000)
//                            val timestampO=System.currentTimeMillis()
//                            api.getLocalTime(ppgDeviceId).subscribe({ calendar ->
//                                val timestamp = calendar.timeInMillis-timestampO // 转换为时间戳
//                                println("时间戳差: $timestamp")
//                                println("response time: "+(System.currentTimeMillis()-timestampO))
//                            }, { throwable ->
//                                println("发生错误: ${throwable.message}")
//                            })
//
//                        }
//                    }
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


//        path = Environment.getExternalStorageDirectory().toString();
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH:mm:ss")
        val resultdate = Date(System.currentTimeMillis())
        val path = getExternalFilesDir(null).toString()+"/"+sdf.format(resultdate);
        Log.d(TAG, "file save path: $path");

//        for(sample in ppgSamples){
//            sample.timestamp=polarTimestamp2UnixTimestamp(sample.timestamp)
//        }


        Utils.saveSamples(ecgSamples,path,sdf.format(resultdate)+"_ecg_samples_"+ecgSR+".txt")
        Utils.saveSamples(ppgSamples,path,sdf.format(resultdate)+"_ppg_samples_"+ppgSR+".txt")
        Utils.saveSamples(hrSamples,path,sdf.format(resultdate)+"_hr_samples_"+ecgSR+".txt")
        Utils.saveSamples(pttSamples,path,sdf.format(resultdate)+"_ptt_samples_"+ppgSR+".txt")
        Utils.saveValueSamples(ecgFilteredSamples,path,sdf.format(resultdate)+"_ecg_filtered_samples_"+ecgSR+".txt")
        Utils.saveValueSamples(ppgFilteredSamples,path,sdf.format(resultdate)+"_ppg_filtered_samples_"+ppgSR+".txt")
    }


    var st=0L
    fun streamPPG() {
        val isDisposed = ppgDisposable?.isDisposed ?: true
        if (isDisposed) {
            val settingMap=HashMap<PolarSensorSetting.SettingType, Int>()
            settingMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = ppgSR
            settingMap[PolarSensorSetting.SettingType.RESOLUTION] = 22
            settingMap[PolarSensorSetting.SettingType.CHANNELS] = 4
            val setting = PolarSensorSetting(settingMap)

            st=System.currentTimeMillis()
            ppgDisposable = api.startPpgStreaming(ppgDeviceId, setting).subscribe(
                { polarPpgData: PolarPpgData ->
                    if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                        assert(polarPpgData.samples.isNotEmpty())
                        if(ppgFirstSampleTimestamp==0L){
                            Log.e(TAG, (System.currentTimeMillis()-st).toString())
                            val pkgTimeLength = ((polarPpgData.samples.size.toDouble())/ppgSR*1000).toLong()
                            ppgAdjustedFirstSampleTimestamp = System.currentTimeMillis()-pkgTimeLength-ppgFirstSampleTimestampOffset
                            ppgAdjustedFirstSampleTimestamp = st+1500
                            ppgFirstSampleTimestamp = polarPpgData.samples[0].timeStamp
                        }
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

    private fun polarTimestamp2UnixTimestamp(vv:Long): Long {
        //Unit: us; Epoch time for polar is 2000-01-01T00:00:00Z
        var v = vv
        v /= MStoNS // ns to ms
        v += PolarEpochTime // set epoch time to unit epoch
        return v
    }

    private fun plotPPG(samples: List<PolarPpgData.PolarPpgSample>){
        val gap = 20
        val ppgSize = ppgSamples.size+samples.size-gap
        val timestamp = System.currentTimeMillis()

        try {
            Log.d(TAG, "PPG data available ${samples.size} Thread:${Thread.currentThread()}")
            if (!ppgReceived) {
                ppgReceived = true
                startTimestamp = System.currentTimeMillis()
            }
            for (data in samples) {
                val value = - data.channelSamples[0].toDouble()
                val sample = Sample((data.timeStamp-ppgFirstSampleTimestamp)/ MStoNS +ppgAdjustedFirstSampleTimestamp, value)
                ppgSamples.add(sample)
            }

            if (ecgSamples.size < ecgPlotterSize+ecgSR || ppgSize < ppgPlotterSize+ppgSR) {
                return
            }

            val ecgPast = DoubleArray(ecgPlotterSize)

            println(ecgSamples.last().timestamp.toString()+" ecg")
            println(ppgSamples.last().timestamp.toString()+" ppg")

            var ecgEnd=ecgSamples.size-1
            while(ecgEnd>=0&&ecgSamples[ecgEnd].timestamp>(ppgSamples[ppgSize-1].timestamp)){
                ecgEnd--
            }
            ecgEnd+=1
            if(ecgEnd-ecgPlotterSize<0)return
            for(i in ecgEnd-ecgPlotterSize until ecgEnd){
                ecgPast[i-(ecgEnd-ecgPlotterSize)]=ecgSamples[i].value
            }

            var ecgRes = ButterworthBandpassFilter.concatenate(ecgPast)
            ecgRes = ButterworthBandpassFilter.ecg250hzBandpassFilter(ecgRes)
            ecgRes = ButterworthBandpassFilter.trimSamples(ecgRes, ecgPlotterSize / 2)


            val ppgPast = DoubleArray(this.ppgPlotterSize)
            var idx = 0
            for (i in ppgSize - this.ppgPlotterSize until ppgSize) {
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

            hrSamples.add(Sample(timestamp,info.HR))
            pttSamples.add(Sample(timestamp,info.PTT))

            //update ptt
            if(info.PTT.toInt() in 1..499){
                updatePtt(String.format("%3.0f ms",info.PTT))
            }else{
                updatePtt("-")
            }

            Log.d(TAG,"PPT: "+info.PTT)

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

            //display info
            val ppgRangeLeft = ppgSamples[ppgSize-ppgPlotterSize].timestamp
            val ppgRangeRight = ppgSamples[ppgSize-1].timestamp
            val ecgRangeLeft = ecgSamples[ecgEnd-ecgPlotterSize].timestamp
            val ecgRangeRight = ecgSamples[ecgEnd-1].timestamp

            Log.d(TAG, "$ppgRangeLeft ppg $ppgRangeRight")
            Log.d(TAG, "$ecgRangeLeft ecg $ecgRangeRight")
            updateInfo(String.format(
                "Filtering...\nDuration: %d sec" +
                        "\nShort Period HR: %.2f" +
                        "\nPPG Time Range: [%d\t,%d]" +
                        "\nECG Time Range: [%d\t,%d]" +
                        "\nStart diff: %d"+
                        "\nEnd diff: %d"
                ,
                ((System.currentTimeMillis() - startTimestamp) / 1000),
                info.HR,
                ppgRangeLeft,ppgRangeRight,
                ecgRangeLeft,ecgRangeRight,
                ppgRangeRight-ecgRangeRight,
                ppgRangeLeft-ecgRangeLeft
                ))
        }catch (e:ConcurrentModificationException){
            e.printStackTrace()
        }catch (e:ArrayIndexOutOfBoundsException){
            e.printStackTrace()
        }catch (e: RuntimeException){
            e.printStackTrace()
        }
    }


    private fun plotECG(sample: Sample) {
        if(ppgReceived){
            sample.timestamp = sample.timestamp - ecgFirstSampleTimestamp + startTimestamp - ecgFirstSampleTimestampOffset
            ecgSamples.add(sample)
        }else{
            ecgFirstSampleTimestamp=sample.timestamp
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