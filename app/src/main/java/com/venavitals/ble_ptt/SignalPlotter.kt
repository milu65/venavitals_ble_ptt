package com.venavitals.ble_ptt

import com.androidplot.xy.AdvancedLineAndPointRenderer
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.XYSeries

class SignalPlotter(title: String, SignalFrequency: Int) {
    companion object {
        private const val TAG = "SignalPlotter"
        private const val SECONDS_TO_PLOT = 5
    }

    private var listener: PlotterListener? = null
    private val plotNumbers: MutableList<Number?>
    val formatter: AdvancedLineAndPointRenderer.Formatter
    private val series: XYSeries
    private var dataIndex = 0

    init {
        val ySamplesSize = SignalFrequency * SECONDS_TO_PLOT
        plotNumbers = MutableList(ySamplesSize) { null }
        formatter = AdvancedLineAndPointRenderer.Formatter()
        formatter.isLegendIconEnabled = false
        series = SimpleXYSeries(plotNumbers, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, title)
    }

    fun getSeries(): SimpleXYSeries {
        return series as SimpleXYSeries
    }

    fun sendSingleSample(mV: Double) {
        sendSingleSampleWithoutUpdate(mV)
        if(dataIndex%20==1)listener?.update()
    }

    fun sendSamples(mV: DoubleArray){
        dataIndex=0;
        for(v in mV){
            plotNumbers[dataIndex++]=v;
        }
        for(i in dataIndex until plotNumbers.size){
            plotNumbers[i]=null
        }

        (series as SimpleXYSeries).setModel(plotNumbers, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY)
        update()
    }

    fun sendSingleSampleWithoutUpdate(mV: Double){
        plotNumbers[dataIndex] = mV
        dataIndex++
        if (dataIndex >= plotNumbers.size - 1) {
            dataIndex = 0
        }
        if (dataIndex < plotNumbers.size - 1) {
            plotNumbers[dataIndex + 1] = null
        } else {
            plotNumbers[0] = null
        }

        (series as SimpleXYSeries).setModel(plotNumbers, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY)
    }

    fun update(){
        listener?.update()
    }

    fun setListener(listener: PlotterListener) {
        this.listener = listener
    }
}