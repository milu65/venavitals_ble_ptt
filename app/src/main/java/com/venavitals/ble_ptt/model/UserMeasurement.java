package com.venavitals.ble_ptt.model;

import java.util.Date;

public class UserMeasurement {
    private Long userID;
    private Long measurementID;
    Date SessionTime;



    public UserMeasurement(long measurementId, long userId, Date sessionTime) {
        this.measurementID = measurementId;
        this.userID = userId;
        this.SessionTime = sessionTime;
    }


    public Long getUserID() {
        return userID;
    }
    public void setUserID(Long userID) {
        this.userID = userID;
    }

    public Long getMeasurementID() {
        return measurementID;
    }
    public void setMeasurementID(Long measurementID) {
        this.measurementID = measurementID;
    }

    public Date getSessionTime() {
        return SessionTime;
    }
    public void setSessionTime(Date SessionTime) {
        this.SessionTime = SessionTime;
    }
}
