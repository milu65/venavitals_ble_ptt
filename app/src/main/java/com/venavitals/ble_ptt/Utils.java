package com.venavitals.ble_ptt;

import static com.venavitals.ble_ptt.filters.ButterworthBandpassFilter.ECG_SR;
import static com.venavitals.ble_ptt.filters.ButterworthBandpassFilter.PPG_SR;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;

import com.github.psambit9791.jdsp.signal.peaks.FindPeak;
import com.github.psambit9791.jdsp.signal.peaks.Spike;

public class Utils {
    private static String TAG = "Utils";

    public static void saveSamples(List<Double> samples, String path, String filename) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean dirCreated = dir.mkdirs();
            if (!dirCreated) {
                Log.e(TAG, "Failed to create directory: " + path);
                return;
            }
        }

        File file = new File(dir, filename);
        try {
            boolean fileCreated = file.createNewFile();
            if (!fileCreated && !file.exists()) {
                Log.e(TAG, "Failed to create file: " + file.getAbsolutePath());
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter osw = new OutputStreamWriter(fos);
                 BufferedWriter bw = new BufferedWriter(osw)) {
                for (Double item : samples) {
                    bw.write(String.valueOf(item));
                    bw.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "file saved("+samples.size()+" samples): "+path + "/" + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static double[] calcPTT(double[] ecgSamples, double[] ppgSamples) {
        // Find peaks
        FindPeak ecgFp = new FindPeak(ecgSamples);
        Spike ecgSpikes = ecgFp.getSpikes();
        int[] ecgOutRightFilter = ecgSpikes.filterByProperty(0.01, 1.0, "right");

        FindPeak ppgFp = new FindPeak(ppgSamples);
        Spike ppgSpikes = ppgFp.getSpikes();
        int[] ppgOutRightFilter = ppgSpikes.filterByProperty(400.0, 20000.0, "right");


        // log debug peaks and samples info
        double max=0;
        double min=0;
        for (int i = 0; i < ecgSamples.length; i++) {
            max=Math.max(max,ecgSamples[i]);
            min=Math.min(min,ecgSamples[i]);
        }
        Log.d(TAG,"ECG min: "+min+" max: "+max);

        min=0;
        max=0;
        for (int i = 0; i < ppgSamples.length; i++) {
            max=Math.max(max,ppgSamples[i]);
            min=Math.min(min,ppgSamples[i]);
        }
        Log.d(TAG,"PPG min: "+min+" max: "+max);

        Log.d(TAG,"ECG Peaks: "+Arrays.toString(ecgOutRightFilter));
        Log.d(TAG,"PPG Peaks: "+Arrays.toString(ppgOutRightFilter));

        // Continuity check
        // HR 40 - 150 (0.4-1.5)
        boolean continuityCheckFlag = true;
        final double MIN_GAP = 0.4;
        final double MAX_GAP = 1.5;
        for (int i = 0; i < ppgOutRightFilter.length; i++) {
            if (i != 0) {
                double gap = (double) ppgOutRightFilter[i] / PPG_SR - (double) ppgOutRightFilter[i - 1] / PPG_SR;
                if (gap < MIN_GAP || gap > MAX_GAP) {
                    continuityCheckFlag = false;
                    break;
                }
            }
        }
        if (continuityCheckFlag) for (int i = 0; i < ecgOutRightFilter.length; i++) {
            if (i != 0) {
                double gap = (double) ecgOutRightFilter[i] / ECG_SR - (double) ecgOutRightFilter[i - 1] / ECG_SR;
                if (gap < MIN_GAP || gap > MAX_GAP) {
                    continuityCheckFlag = false;
                    break;
                }
            }
        }
        if (!continuityCheckFlag) return new double[]{0,0};

        // Calc PTT and HR
        double PTTSum = 0;
        int PTTCounter = 0;
        double HRSum = 0;
        int offset = 0;

        int minLen = Math.min(ppgOutRightFilter.length, ecgOutRightFilter.length);
        for (int i = 0; i < minLen; i++) {
            if (i == 0) {
                if ((double) ppgOutRightFilter[i] / PPG_SR - (double) ecgOutRightFilter[i] / ECG_SR < 0.1) {
                    offset = 1;
                }
            } else {
                HRSum += ecgOutRightFilter[i] - ecgOutRightFilter[i - 1];
            }
            if (i + offset >= ppgOutRightFilter.length) break;
            PTTCounter++;
            PTTSum += (ppgOutRightFilter[i + offset] * 1000.0 / PPG_SR - ecgOutRightFilter[i] * 1000.0 / ECG_SR);
        }

        double HR = ECG_SR * 60 / (HRSum / (ppgOutRightFilter.length - 1));
        double PTT = PTTSum / PTTCounter;
        return new double[]{HR,PTT};
    }
}
