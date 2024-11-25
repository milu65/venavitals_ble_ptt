package com.venavitals.ble_ptt.signal;

import static com.venavitals.ble_ptt.signal.filters.ButterworthBandpassFilter.ECG_SR;
import static com.venavitals.ble_ptt.signal.filters.ButterworthBandpassFilter.PPG_SR;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

import com.github.psambit9791.jdsp.signal.peaks.FindPeak;
import com.github.psambit9791.jdsp.signal.peaks.Spike;

import org.jetbrains.annotations.NotNull;

public class Utils {
    private static String TAG = "Utils";


    public static void saveSamples(@NotNull List<Sample> samples, @NotNull String path, @NotNull String filename) {

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
                for (Sample item : samples) {
                    bw.write(item.timestamp+","+ item.value);
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

    public static void saveValueSamples(List<Double> samples, String path, String filename) {
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
            Log.d(TAG, "file saved("+samples.size()+" value samples): "+path + "/" + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static SignalInfo calcPTT(double[] ecgSamples, double[] ppgSamples) {
        final int minPTT=0;
        final int maxPTT=800;

        SignalInfo info=new SignalInfo();
        // log debug peaks and samples info
        double max=0;
        double min=0;
        for (double ecgSample : ecgSamples) {
            max = Math.max(max, ecgSample);
            min = Math.min(min, ecgSample);
        }
//        Log.d(TAG,"ECG min: "+min+" max: "+max);
        info.ecgMaxValue=max;
        info.ecgMinValue=min;

        double ecgThreshold=(max-min)*0.6;

        min=0;
        max=0;
        for (double ppgSample : ppgSamples) {
            max = Math.max(max, ppgSample);
            min = Math.min(min, ppgSample);
        }
//        Log.d(TAG,"PPG min: "+min+" max: "+max);
        info.ppgMaxValue=max;
        info.ppgMinValue=min;

        double ppgThreshold=(max-min)*0.8;

        // Find peaks
        FindPeak ecgFp = new FindPeak(ecgSamples);
        Spike ecgSpikes = ecgFp.getSpikes();
        int[] ecgOutRightFilter = ecgSpikes.filterByProperty(ecgThreshold, 1.0, "right");

        FindPeak ppgFp = new FindPeak(ppgSamples);
        Spike ppgSpikes = ppgFp.getSpikes();
        int[] ppgOutRightFilter = ppgSpikes.filterByProperty(125.0, 20000.0, "left");

        info.ppgPeaks=ppgOutRightFilter.length;
        info.ecgPeaks=ecgOutRightFilter.length;
//        System.out.println("ECG Peaks: "+Arrays.toString(ecgOutRightFilter));
//        System.out.println("PPG Peaks: "+Arrays.toString(ppgOutRightFilter));

        // Continuity check
        // HR 40 - 150 (0.4-1.5)
        boolean continuityCheckFlag = true;
        final double MIN_GAP = 0.4;
        final double MAX_GAP = 1.5;

        double ecgGapSum=0;
        int ecgGapCounter=0;
        for (int i = 0; i < ecgOutRightFilter.length; i++) {
            if (i != 0) {
                double gap = (double) ecgOutRightFilter[i] / ECG_SR - (double) ecgOutRightFilter[i - 1] / ECG_SR;
                if(ecgGapCounter==0){
                    ecgGapSum+=gap;
                    ecgGapCounter++;
                }
                double average=ecgGapSum/ecgGapCounter;
                double diff=Math.abs(gap-average);
//                System.out.println(i+" avg:"+average+" gap:"+gap+" diff:"+diff/average);
                if (gap < MIN_GAP || gap > MAX_GAP||diff/average>=0.35) {
                    continuityCheckFlag = false;
                    break;
                }
                if(ecgGapCounter>1){
                    ecgGapSum+=gap;
                    ecgGapCounter++;
                }
            }
        }

        double HRSum = 0;
        if (continuityCheckFlag){ //HR calc
            for(int k=0;k<ecgOutRightFilter.length;k++){
                if(k!=0) {
                    HRSum += ecgOutRightFilter[k] - ecgOutRightFilter[k - 1];
                }
            }
            info.HR= ECG_SR * 60.0 / (HRSum / (ecgOutRightFilter.length - 1));
        }

        double ppgGapSum=0;
        int ppgGapCounter=0;
        if (continuityCheckFlag)for (int i = 0; i < ppgOutRightFilter.length; i++) {
            if (i != 0) {
                double gap = (double) ppgOutRightFilter[i] / PPG_SR - (double) ppgOutRightFilter[i - 1] / PPG_SR;
                if(ppgGapCounter==0){
                    ppgGapSum+=gap;
                    ppgGapCounter++;
                }
                double average=ppgGapSum/ppgGapCounter;
                double diff=Math.abs(gap-average);
//                System.out.println(i+" avg:"+average+" gap:"+gap+" diff:"+diff/average);
                if (gap < MIN_GAP || gap > MAX_GAP||diff/average>=0.35) {
                    continuityCheckFlag = false;
                    break;
                }
                if(ppgGapCounter>1){
                    ppgGapSum+=gap;
                    ppgGapCounter++;
                }
            }
        }
        if (!continuityCheckFlag){
            Log.d(TAG,"signal continuity check failed");
            return info;
        }

        // Calc PTT and HR
        double PTTSum = 0;
        int PTTCounter = 0;
        int i=0;
        int j=0;
        for(;i<ecgOutRightFilter.length;i++){ //find first valuable ECG and PPG peaks
            double ecgTime=ecgOutRightFilter[i]/(double)ECG_SR;
            for(;j<ppgOutRightFilter.length;j++){
//                System.out.println(ecgOutRightFilter[i]/(double)ECG_SR+" "+ppgOutRightFilter[j]/(double)PPG_SR);
                if(ppgOutRightFilter[j]/(double)PPG_SR>ecgTime){
                    break;
                }
            }
            if(j>=ppgOutRightFilter.length){
                break;
            }
            if((ppgOutRightFilter[j]/(double)PPG_SR-ecgTime)*1000<maxPTT){
                break;
            }
        }
        int minLen = Math.min(ppgOutRightFilter.length-j, ecgOutRightFilter.length-i);
        for(int k=0;k<minLen;k++){ //PTT calc
            PTTCounter++;
            PTTSum += (ppgOutRightFilter[j+k] * 1000.0 / PPG_SR - ecgOutRightFilter[i+k] * 1000.0 / ECG_SR);
        }

        double PTT = PTTSum / PTTCounter;

        info.PTT=PTT;
        if(PTT<minPTT||PTT>maxPTT){
            Log.d(TAG,"wrong PTT: "+PTT);
        }

        return info;
    }

    public static void useThreadToSendFile(String filePath, String point){
        Thread t=new Thread(()->{
            sendFile(filePath,point);
        });
        t.start();
    }
    public static void useThreadToSendFile(String filePath){
        Thread t=new Thread(()->{
            sendFile(filePath);
        });
        t.start();
    }

    public static void sendFile(String filePath){
        sendFile(filePath,null);
    }

    public static synchronized void sendFile(String filePath,String point) {
        if(point==null)point="0";
        String serverIp = "192.168.0.84"; // Windows
//        String serverIp = "192.168.228.243"; // Mi
        int serverPort = 8848; // Python服务器监听的端口

        try (Socket socket = new Socket(serverIp, serverPort);
             FileInputStream fileInputStream = new FileInputStream(filePath);
             BufferedOutputStream outputStream = new BufferedOutputStream(socket.getOutputStream())) {

            // 发送文件名
            String fileName = new File(filePath).getName();
            outputStream.write((fileName + "\n").getBytes());
            outputStream.write((point+",0.0\n").getBytes());
            outputStream.flush();

            // 发送文件内容
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            System.out.println("文件已成功发送: " + filePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    final public static int MStoNS = 1000_000;
    final private static long PolarEpochTime = 946684800000L;
    public static Long  polarTimestamp2UnixTimestamp(Long vv) {
        //Unit: us; Epoch time for polar is 2000-01-01T00:00:00Z
        long v = vv;
        v /= MStoNS; // ns to ms
        v += PolarEpochTime; // set epoch time to unit epoch
        return v;
    }

}
