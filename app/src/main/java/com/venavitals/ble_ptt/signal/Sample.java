package com.venavitals.ble_ptt.signal;

public class Sample {
    public Long timestamp;
    public Double value;

    public Sample(Long timestamp, Double value) {
        this.timestamp = timestamp;
        this.value = value;
    }
}
