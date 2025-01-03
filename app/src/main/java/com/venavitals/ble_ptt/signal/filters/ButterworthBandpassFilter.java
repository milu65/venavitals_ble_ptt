package com.venavitals.ble_ptt.signal.filters;

import com.venavitals.ble_ptt.signal.Sample;
import java.util.Arrays;
import java.util.List;

public class ButterworthBandpassFilter {
    public static int PPG_SR=176;  //28Hz, 44Hz, 55Hz, 135Hz, 176Hz
    public static int ECG_SR=250;

    public static double[] concatenate(double[]arr){ //mirror padding
        int l=0;
        if(arr.length%2!=0)l++;

        int half=arr.length/2;

        double[] newArr=new double[(arr.length-l)*2];

        for(int i=0;i<half;i++){
            newArr[half-i-1]=arr[i];
            newArr[newArr.length-i-1]=arr[half+i];
        }
        System.arraycopy(arr, l, newArr,half, arr.length-l);

        return newArr;
    }

    public static double[] concatenate(List<Sample> al, int start, double[]arr){ //mirror padding on right half
        int l=0;
        if(arr.length%2!=0)l++;

        int half=arr.length/2;

        double[] newArr=new double[(arr.length-l)*2];

        for(int i=0;i<half;i++){
            newArr[i]=al.get(i+start).value;
            newArr[newArr.length-i-1]=arr[half+i];
        }
        System.arraycopy(arr, l, newArr,half, arr.length-l);

        return newArr;
    }

    private static double[] filter(double[] b, double[] a, double[] x) {
        int n = x.length;
        double[] y = new double[n];
        int order = b.length - 1;

        // init
        for (int i = 0; i < order; i++) {
            y[i] = 0;
        }

        // forward filtering
        for (int i = order; i < n; i++) {
            y[i] = 0;
            for (int j = 0; j < b.length; j++) {
                if (i - j >= 0) {
                    y[i] += b[j] * x[i - j];
                }
            }
            for (int j = 1; j < a.length; j++) {
                if (i - j >= 0) {
                    y[i] -= a[j] * y[i - j];
                }
            }
            y[i] /= a[0];
        }

        return y;
    }

    // Bidirectional filtering
    public static double[] filtfilt(double[] b, double[] a, double[] x) {
        // forward filtering
        double[] y = filter(b, a, x);

        // Flip the array
        double[] reverseX = reverse(y);

        double[] y2 = filter(b, a, reverseX);

        return reverse(y2);
    }

    public static double[] reverse(double[] x) {
        double[] reversed = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            reversed[i] = x[x.length - 1 - i];
        }
        return reversed;
    }

    public static double[] ppgBandpassFilter(double[]x){
        if(PPG_SR==176){
            return ppg176hzBandpassFilter(x);
        }else if(PPG_SR==55){
            return ppg55hzBandpassFilter(x);
        }else {
            return ppg55hzBandpassFilter(x);
        }
    }

    public static double[] ppg55hzBandpassFilter(double[] x){
        /* MATLAB
            fs=55;
            f_low = 0.5;
            f_high = 25;
            [b, a] = butter(2, [f_low, f_high]/(fs/2), 'bandpass');
         */
        //Order 1
//        double[] b = {0.8525,0,-0.8525};
//        double[] a = {1.0000,-0.1972,-0.7049};

        //Order 2
//        double[] b = {0.7845,0,-1.5690,0,0.7845};
//        double[] a = {1.0000,-0.3195,-1.4800,0.1939,0.6160};

//        1.2-5
        double[] b = {0.0357,0,-0.0715,0,0.0357};
        double[] a = {1.0000,-3.2642,4.1108,-2.3835,0.5415};
        return filtfilt(b, a, x);
    }

    public static double[] ppg176hzBandpassFilter(double[] x){
        /* MATLAB
            fs=176;
            f_low = 1;
            f_high = 5;
            [b, a] = butter(2, [f_low, f_high]/(fs/2), 'bandpass');
         */

        //Order 2
        double[] b = {0.0046,0,-0.0092,0,0.0046};
        double[] a = {1.0000,-3.7865,5.3914,-3.4220,0.8171};
        return filtfilt(b, a, x);
    }
    public static double[] ecg250hzBandpassFilter(double[] x){
        /* MATLAB
            fs = 250;
            f_low = 5;
            f_high = 40;
            [b, a] = butter(2, [f_low, f_high]/(fs/2), 'bandpass');
         */
        //Order 1
//        double[] b = {0.3200,0,-0.3200};
//        double[] a = {1.0000,-1.2691,0.3600};

        //Order 2
        double[] b = {0.1174,0,-0.2347,0,0.1174};
        double[] a = {1.0000,-2.6363,2.6711,-1.3199,0.2946};
        return filtfilt(b, a, x);
    }

    public static double[] trimSamples(double[] samples, double trimRatio) {
        int length = samples.length;
        int start = (int) (length * trimRatio);
        int end = (int) (length * (1 - trimRatio));
        return Arrays.copyOfRange(samples, start, end);
    }


    public static double[] trimSamples(double[] samples, int trimSize) {
        int length = samples.length;
        int end = length - trimSize;
        return Arrays.copyOfRange(samples, trimSize, end);
    }

}
