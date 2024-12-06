package com.venavitals.ble_ptt.model;

import com.venavitals.ble_ptt.network.JsonUtil;
import com.venavitals.ble_ptt.signal.Sample;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MeasurementData {

    private String  ecgSamples;
    private String  ppgSamples;
    private String  pttSamples;
    private String  ecgFilteredSamples;
    private String  ppgFilteredSamples;
    private String  hrSamples;
    private Long userId;

    //Constructor, note that the incoming parameters should be converted to JSON format first
    public MeasurementData(
            List<Sample> ecgSamples,
            List<Sample> ppgSamples,
            List<Sample> pttSamples, LinkedList<Double> ecgFilteredSamples,
            LinkedList<Double> ppgFilteredSamples, List<Sample> hrSamples, Long userId) {
        this.userId = userId;
        this.ecgSamples = JsonUtil.toJson(ecgSamples);
        this.ppgSamples = JsonUtil.toJson(ppgSamples);
        this.pttSamples = JsonUtil.toJson(pttSamples);
        this.ecgFilteredSamples = JsonUtil.toJson(ecgFilteredSamples);
        this.ppgFilteredSamples = JsonUtil.toJson(ppgFilteredSamples);
        this.hrSamples = JsonUtil.toJson(hrSamples);
    }

    public Long getUserId(){
        return this.userId;
    }

}
