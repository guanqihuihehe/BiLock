package me.domin.bilock;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;
//import com.chrischen.waveview.WaveView;
import com.example.administrator.mfcc.MFCC;
//import com.maple.recorder.recording.AudioChunk;
//import com.maple.recorder.recording.AudioRecordConfig;
//import com.maple.recorder.recording.MsRecorder;
//import com.maple.recorder.recording.PullTransport;
//import com.maple.recorder.recording.Recorder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


import github.bewantbe.audio_analyzer_for_android.STFT;

import static android.content.ContentValues.TAG;
import static me.domin.bilock.TrainPresenter.MEAN;
import static me.domin.bilock.TrainPresenter.NONE;
import static me.domin.bilock.TrainPresenter.SD;
import static me.domin.bilock.TrainPresenter.SUM;
import static me.domin.bilock.TrainPresenter.Z_SCORE;
import static me.domin.bilock.TrainPresenter.max;

/**
  * @ProjectName:    Bilock
  * @Package:        me.domin.bilock
  * @ClassName:      LockPresenter
  * @Description:    处理锁屏界面 LockScreenActivity 的逻辑业务，包括从移动端获取录音，传递数据给Model，并根据结果更新UI
  * @Author:         Administrator
  * @CreateDate:     2018/4/14
  * @UpdateUser:     July
  * @UpdateDate:
  * @UpdateRemark:   将trainData,trainModel等方法移动到TrainPresenter类中，更改写入文件的路径
  * @Version:        1.0
 */

public class LockPresenter implements LockContract.Presenter {



    public static String absolutePath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/Bilock/";
    private static String dictionaryPath = absolutePath + "/MFCC2/";
    public STFT stft;   // use with care
    static final  String TAG="LockPresenter";
    public int type=NONE;
    public static final int NONE=0;
    public static final int Z_SCORE=1;
    public static final int SUM=2;
    public static final int MEAN=3;
    public static final int SD=4;
    public static final int LEGAL=10;
    public static final int ILLEGAL=11;

    static int FEATURE_NUM=TrainPresenter.FEATURE_NUM;

    private LockContract.View mLockView;


    private WaveFileReader mWaveReader;

    public LockPresenter(LockContract.View view){
        mLockView=view;
    }

    /*载入MFCC的c代码库*/
    static {
        System.loadLibrary("native-lib");
    }

    private boolean isRecord = false;

    /**
     　　* @Title: initRecorder
     　　* @Description:    实例化一个AudioRecord对象并开始录音，设置相关参数
     　　* @param void
     　　* @return void
     　　*/

    public void initRecorder() {
        record = new AudioRecord(6, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, 2 * bufferSampleSize);
        record.startRecording();
        bufferSampleSize = (int) Math.ceil(1.0 * sampleRate / bufferSampleSize) * bufferSampleSize;
    }


    @Override
    public void svmTrain() {
        try {
            MFCC.svmTrain();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopRecord() {
        if(task!=null&&!task.isCancelled()){
            task.cancel(true);
        }
        isRecord = false;
        if(record!=null) {
            record.stop();
            record.release();
        }
    }

    int tester=10;
    //修改日期：2019/10/6 将LockPresenter开始的步骤封装到该方法中，需要传入数据处理的方法，UI 直接调用就行
    @Override
    public void startRecord(int type,int tester) {
        isRecord=true;
        initRecorder();
        currentRecordTaskNew();
        this.type=type;
        this.tester=tester;

    }


     double[][] values=new double[100][FEATURE_NUM];
     double max_distance=0,ave_distance=0;
     int data_num=0;
    public void initData(){
        Log.d(TAG,"initdata is used");
        String path=FileUtil.getFilePathName(FileUtil.MODEL_DATA2);
        try{
            BufferedReader br=new BufferedReader(new FileReader(path));
            int line_num=0;
            String first=br.readLine();
            String second=br.readLine();
             max_distance=Double.valueOf(second.substring(second.indexOf(":")+1));
            ave_distance=Double.valueOf(first.substring(first.indexOf(":")+1));
            String feature;
            while((feature=br.readLine())!=null){
                String[] features=feature.split(" ");
                for(int i=1;i<FEATURE_NUM&&i<features.length;i++){
                    values[line_num][i-1]=Double.valueOf((features[i].split(":"))[1]);
                }
                line_num++;
            }
            data_num=line_num;
        }catch(IOException e){
            Log.d(TAG,"MODEL DATE MISSING!");
        }

    }


    @Override
    public void currentRecord() {

    }


    CurrentRecordTaskNew task;
    @Override
    public void currentRecordTaskNew() {
        if(task!=null&&!task.isCancelled()){
            task.cancel(true);
        }
        isRecord = true;
        task=(new CurrentRecordTaskNew());
        task.execute();
        /*ExecutorService executor = Executors.newFixedThreadPool(3);
        executor.execute(new CurrentRecordTaskByRun());*/

    }




    @Override
    public boolean hasModel() {
        File file = new File(dictionaryPath);
        file.mkdir();
        File[] files = file.listFiles();
        for (File file1 : files) {
            if (file1.getName().equals("data_model.txt"))
                return true;
        }

        return false;
    }



    private int[] getPeaks(double[] signal) {
        int[] result = new int[2];
        double max = 0;
        for (int i = 0; i < signal.length; i++) {
            if (max < signal[i]) {
                max = signal[i];
                result[0] = i;
            }
        }

        max = 0;
        for (int i = 0; i < signal.length; i++) {
            if (max < signal[i] && (i > result[0] + 1000 || i < result[0] - 1000)) {
                max = signal[i];
                result[1] = i;
            }
        }

        Arrays.sort(result);

//        Log.d(TAG, "getPeaks: 1 = " + result[0] + " 2 = " + result[1]);
        return result;
    }


    /*
                @description: 用于获取BufferedWriter对象
                修改时间：2019/6/5
                修改内容：改变参数
                修改人：July
             */
    private BufferedWriter createBufferedWriter(String name) throws IOException {
        File file = new File(name);
        if (!file.getParentFile().exists())
            file.getParentFile().mkdir();
        return new BufferedWriter(new FileWriter(file));
    }




    private void writeData(Double feature[], BufferedWriter bw) {

        try {
            bw.write("-1 ");
            for (int i = 0; i < feature.length; i++) {
                bw.write((i + 1) + ":" + String.valueOf(feature[i]));
                if (i != feature.length - 1)
                    bw.write(" ");
//                Log.d(TAG, "writeModel: feature = " + feature[i]);
            }

            bw.write(" \n");
            bw.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * @description:得到峰值索引的那一段buffera，双峰值，暂时也没用
     * @param a
     * @param b
     * @param sample
     * @return double[]
     */
    private double[] getBufferBetween(int a, int b, double[] sample) {

//        Log.d(TAG, "getBufferBetween: a = " + a + " b = " + b);

        LinkedList<Double> bufferList = new LinkedList<>();
        //-100 ~ +200
        if (a - 100 > 0) {
            for (int i = 0; i <= 300 && a - 100 + i < sample.length; i++) {
//                if ((int) sample[a - 100 + i] != 0)
                bufferList.add(sample[a - 100 + i]);
            }
        } else {
            for (int i = 0; i < sample.length && i <= 300 - a; i++) {
                bufferList.add(sample[i]);
            }
        }

        if (b - 100 > 0) {
            for (int i = 0; i <= 300 && b - 100 + i < sample.length; i++) {
//                if ((int) sample[a - 100 + i] != 0)
                bufferList.add(sample[b - 100 + i]);
            }
        } else {
            for (int i = 0; i < sample.length && i <= 300 - b; i++) {
                bufferList.add(sample[i]);
            }
        }

//        if (b - 401 + i > 0)
//            for (; i <= 600 && b - 401 + i < sample.length; i++) {
////                if ((int) sample[b - 401 + i] != 0)
//                bufferList.add(sample[b - 401 + i]);
//
//            }

        double[] buffer = new double[bufferList.size()];
        for (int j = 0; j < buffer.length; j++) {
            buffer[j] = bufferList.get(j);
        }
        return buffer;
    }





    public AudioRecord record = null;
    int sampleRate = 44100;
    int fftlen = 1024;
    //AudioRecord录音需要的最小缓存数组大小
    int minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
    int numOfReadShort;
    int readChunkSize = fftlen;  // Every hopLen one fft result (overlapped analyze window)
    short[] audioSamples = new short[readChunkSize];
    int bufferSampleSize = Math.max(minBytes / 2, fftlen / 2) * 2;

    public int getMax() {

        // tolerate up to about 1 sec.

        int max = 0;
        for (int i = 0; i < audioSamples.length; i++) {
            if (max < audioSamples[i])
                max = audioSamples[i];
        }
        return max;
    }

    /*
                修改日期：2019/6/8
                内容：修改文件路径
                修改人：July
             */
    
    /**
     　　* @ClassName: CurrentRecordTaskNew
     　　* @Description: 用于读取录音数据，用WavWriter处理数据并提取特征，判断是否合法
     　　* @author Administrator
     　　* ${tags}
     　　*/

    
    class CurrentRecordTaskNew extends AsyncTask<Void, Boolean, Void> {
        WavWriter wavWriter = new WavWriter(WavWriter.TEST, sampleRate);


        @SuppressLint("NewApi")
        @Override
        /**  
            * @Title: doInBackground   
        　　* @Description: 相当于run方法，完成读取录音数据并进行处理的功能
        　　* @param [voids]       
        　　* @return java.lang.Void       
        　　* @throws   
        　　*/
        protected Void doInBackground(Void... voids) {
//            int bufferSampleSize = Math.max(minBytes / 2, fftlen / 2) * 2;
            Log.d(TAG, "AsyncTask is made"+Thread.currentThread().getId());
            while (!isCancelled()) {
                wavWriter.start();
                int max = 0;
                int num = 0;
                //max < MAX_NOISE
                //一直读取录音数据，直到获取两个在阈值范围内的峰值，视作牙齿咬合声音
                while (num != 2 && isRecord) {
                    if(isCancelled())
                        return null;
                    numOfReadShort = record.read(audioSamples, 0, readChunkSize);   // pulling
                    max = wavWriter.pushAudioShortNew(audioSamples, numOfReadShort);  // Maybe move this to another thread?
                    mLockView.updateMax(max);
                    if (max == -1)
                        num++;
                }

                if (!isRecord) {
                    record.stop();
                    record.release();
                   // wavWriter.stop();
                    return null;
                }
                //获取wavWriter提取好的声音峰值
                int[] signal = wavWriter.getSignal();

                BufferedWriter bw = null;
                //让wavwriter更新wav文件长度信息
                wavWriter.stop();
                //double energy = getEnergy(signal);
                Log.d(TAG," i am here");

                //将int类型的声音数据转换为double类型数据

                byte[] buffer = new byte[signal.length * 2];
                for (int i = 0; i < signal.length; i++) {
                    buffer[2 * i] = (byte) (signal[i] & 0xff);
                    buffer[2 * i + 1] = (byte) ((signal[i] >> 8) & 0xff);
                }


                //用MFCC获取声音的特征值，存入featureDouble数组中
                Double[] featureDouble = new Double[FEATURE_NUM];
                String recordPath = FileUtil.getFilePathName(FileUtil.TEST_RECORD);
                try {
                    System.arraycopy(getMFCCFeatures(recordPath, buffer, signal.length),
                            0, featureDouble, 0, MFCC.LEN_MELREC);
                    //featureDouble[FEATURE_NUM - 1] = energy;
                    //       featureDouble=z_score(featureDouble);
                    // featureDouble=sum_normal(featureDouble);
                    //            featureDouble[featureDouble.length - 1] = getRMS(signal, result);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //        normalizationData(maxBuffer, minBuffer, featureDouble);

                //        File file2 = new File(dictionaryPath + "testRecord.txt");
                String path = FileUtil.getFilePathName(FileUtil.TEST_FEATURE);
                try {
                    bw = createBufferedWriter(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }


                //将所有MFCC特征写入文件
                //将数据存入文件
                //            Log.d(TAG, "writeModel: flag = " + flag + " len = " + feature.length);

                writeData(featureDouble, bw);


                //调用svmPredict方法判断特征是否合法，并调用publishProgress更新结果
                   /* try {
                        if ((MFCC.svmPredict(path)) == 1) {
                            publishProgress(true);
                        } else {
                            publishProgress(false);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/

                if (ONE_CLASS_KNN(featureDouble, type,tester)) {
                    publishProgress(true);
                } else {
                    publishProgress(false);
                }
            }
            Log.d(TAG, "AsyncTask come to the end"+Thread.currentThread().getId());
            return null;
        }
        public Double[] getMFCCFeatures(String path,byte[] buffer,int length) throws IOException{
            File file=new File(path);
            File parent=new File(file.getParent());
            parent.mkdirs();
            return MFCC.getFeatures(path, buffer, length, 44100);

        }
        public double getEnergy(int[] signal){
            double energy=0;
            for(int i=0;i<signal.length;i++){
                energy+=signal[i];
            }
            energy/=signal.length;
            energy=Math.abs(energy);
            energy=Math.log(energy)/Math.log(2);
            return energy;
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        public boolean KNN(Double[] features,final int normal_type){
            switch(normal_type){
                case Z_SCORE:  features=z_score(features);break;
                case MEAN: features=mean_normal(features);break;
                case SUM:    features=sum_normal(features);break;
                case SD: features=sd_normal(features);break;
                default:;
            }
            double[][] values=TrainPresenter.values;
            int[] user=TrainPresenter.user;
            ArrayList list=new ArrayList();
            for(int i=0;i<TrainPresenter.count;i++){
                Test2Activity.Point p=new Test2Activity.Point(values[i],user[i]);
                p.calDis(features);
                list.add(p);
            }
            if(list.size()==0)
                return false;
            list.sort(new Comparator() {
                @Override
                public int compare(Object o, Object t1) {
                    double dis1=((Test2Activity.Point)o).distance;
                    double dis2=((Test2Activity.Point)t1).distance;
                    if(dis1>dis2)
                        return 1;
                    else if(dis1==dis2)
                        return 0;
                    else
                        return -1;
                }
            });
            int legal_count=0,illegal_count=0;
            for(int i=0;i<10;i++){
                if(((Test2Activity.Point)list.get(i)).user==1)
                    legal_count++;
                else
                    illegal_count++;
            }
            Log.d(TAG,"The number of legal point:"+Integer.toString(legal_count));
            if(legal_count>illegal_count-1)
                return true;
            else
                return false;
        }
        public boolean ONE_CLASS_KNN(Double[] features,final int normal_type,final int tester){
            Log.d(TAG,"One class knn is used");
            switch(normal_type){
                case Z_SCORE:  features=z_score(features);break;
                case MEAN: features=mean_normal(features);break;
                case SUM:    features=sum_normal(features);break;
                case SD: features=sd_normal(features);break;
                default:;
            }
            ArrayList list=new ArrayList();
            Log.d(TAG,"ave:"+ave_distance);
            Log.d(TAG,"max:"+max_distance);
            int user_count=0;
            double ave_dis=0;
            for(int i=0;i<data_num;i++){
                ave_dis+=getDis(features,values[i]);
                    user_count++;
            }
            ave_dis/=user_count;
            Log.d(TAG,"ave_dis:"+ave_dis);
            if(tester==LEGAL){
                if(ave_dis<ave_distance+(max_distance-ave_distance)/3  ) {
                    return true;
                }else{
                    return false;
                }
            }else{
                if(ave_dis<ave_distance+(max_distance-ave_distance)/5  ) {
                    return true;
                }else{
                    return false;
                }
               //return false;
            }


        }

        /*
    2019/10/14 调整1.2.3.6.7.8特征比重，降低其他比重
 */
        public double getDis(Double[] p1,double[] p2){
            double dis=0;
            for(int i=0;i<FEATURE_NUM;i++){
                switch(i+1) {
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                    case 6:
                    case 7:
                    case 8:
                        dis += 1.2 * Math.pow(p1[i] - p2[i], 2);
                    default:
                        dis += 0.8 * Math.pow(p1[i] - p2[i], 2);
                }
            }
            dis=Math.sqrt(dis);
            return dis;
        }
        private Double[] sd_normal(Double[] feature){
            double[] sd=TrainPresenter.sd;
            for(int i=0;i<FEATURE_NUM;i++){
                feature[i]=feature[i]/sd[i];
            }
            return feature;
        }
        private Double[] sum_normal(Double[] feature){
            double[] sum=TrainPresenter.sum;
            for(int i=0;i<FEATURE_NUM;i++){
                feature[i]=feature[i]/sum[i];
            }
            return feature;
        }
        private Double[] mean_normal(Double[] feature){
            double[] max=TrainPresenter.max;
            double[] min=TrainPresenter.min;
            for(int i=0;i<FEATURE_NUM;i++){
                feature[i]=(feature[i]-min[i])/(max[i]-min[i]);
            }
            return feature;
        }
        private Double[] z_score(Double[] feature){
            double[] variance=TrainPresenter.variance;
            double[] average=TrainPresenter.average;
            for(int i=0;i<FEATURE_NUM;i++){
                feature[i]=(feature[i]-average[i])/variance[i];
            }
            return feature;
        }

        @Override
        /**
            * @Title: onProgressUpdate
        　　* @Description: 根据publishProgress方法传入的value值更新UI线程
        　　* @param [values]
        　　* @return void
        　　* @throws
        　　*/
        protected void onProgressUpdate(Boolean... values) {
            if (values[0])
                mLockView.unlockSuccess();
            else mLockView.unlockFail();
        }

    }

    class CurrentRecordTaskByRun implements Runnable{
        WavWriter wavWriter = new WavWriter(WavWriter.TEST, sampleRate);

        @Override
        public void run() {
            Log.d(TAG, "Currenttask is made"+Thread.currentThread().getId());
            while(true) {
                wavWriter.start();

                int max = 0;
                int num = 0;
                //max < MAX_NOISE
                //一直读取录音数据，直到获取两个在阈值范围内的峰值，视作牙齿咬合声音
                while (num != 2 && isRecord) {
                    numOfReadShort = record.read(audioSamples, 0, readChunkSize);   // pulling
                    max = wavWriter.pushAudioShortNew(audioSamples, numOfReadShort);  // Maybe move this to another thread?
                    mLockView.updateMax(max);
                    if (max == -1)
                        num++;
                }

                if (!isRecord) {
                    record.stop();
                    record.release();
                    // wavWriter.stop();

                }
                //获取wavWriter提取好的声音峰值
                int[] signal = wavWriter.getSignal();

                BufferedWriter bw = null;
                //让wavwriter更新wav文件长度信息
                wavWriter.stop();
                //double energy = getEnergy(signal);
                Log.d(TAG, " i am here");

                //将int类型的声音数据转换为double类型数据

                byte[] buffer = new byte[signal.length * 2];
                for (int i = 0; i < signal.length; i++) {
                    buffer[2 * i] = (byte) (signal[i] & 0xff);
                    buffer[2 * i + 1] = (byte) ((signal[i] >> 8) & 0xff);
                }


                //用MFCC获取声音的特征值，存入featureDouble数组中
                Double[] featureDouble = new Double[FEATURE_NUM];
                String recordPath = FileUtil.getFilePathName(FileUtil.TEST_RECORD);
                try {
                    System.arraycopy(getMFCCFeatures(recordPath, buffer, signal.length),
                            0, featureDouble, 0, MFCC.LEN_MELREC);
                    //featureDouble[FEATURE_NUM - 1] = energy;
                    //       featureDouble=z_score(featureDouble);
                    // featureDouble=sum_normal(featureDouble);
                    //            featureDouble[featureDouble.length - 1] = getRMS(signal, result);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //        normalizationData(maxBuffer, minBuffer, featureDouble);

                //        File file2 = new File(dictionaryPath + "testRecord.txt");
                String path = FileUtil.getFilePathName(FileUtil.TEST_FEATURE);
                try {
                    bw = createBufferedWriter(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }


                //将所有MFCC特征写入文件
                //将数据存入文件
                //            Log.d(TAG, "writeModel: flag = " + flag + " len = " + feature.length);

                writeData(featureDouble, bw);

                if (ONE_CLASS_KNN(featureDouble, type, tester)) {
                    publishProgress(true);
                    Log.d(TAG, "CurrentTask come to the end"+Thread.currentThread().getId());
                    return;
                } else {
                    publishProgress(false);
                }
            }


            //return ;
        }
        public Double[] getMFCCFeatures(String path,byte[] buffer,int length) throws IOException{
            File file=new File(path);
            File parent=new File(file.getParent());
            parent.mkdirs();
            return MFCC.getFeatures(path, buffer, length, 44100);

        }
        public double getEnergy(int[] signal){
            double energy=0;
            for(int i=0;i<signal.length;i++){
                energy+=signal[i];
            }
            energy/=signal.length;
            energy=Math.abs(energy);
            energy=Math.log(energy)/Math.log(2);
            return energy;
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        public boolean KNN(Double[] features,final int normal_type){
            switch(normal_type){
                case Z_SCORE:  features=z_score(features);break;
                case MEAN: features=mean_normal(features);break;
                case SUM:    features=sum_normal(features);break;
                case SD: features=sd_normal(features);break;
                default:;
            }
            double[][] values=TrainPresenter.values;
            int[] user=TrainPresenter.user;
            ArrayList list=new ArrayList();
            for(int i=0;i<TrainPresenter.count;i++){
                Test2Activity.Point p=new Test2Activity.Point(values[i],user[i]);
                p.calDis(features);
                list.add(p);
            }
            if(list.size()==0)
                return false;
            list.sort(new Comparator() {
                @Override
                public int compare(Object o, Object t1) {
                    double dis1=((Test2Activity.Point)o).distance;
                    double dis2=((Test2Activity.Point)t1).distance;
                    if(dis1>dis2)
                        return 1;
                    else if(dis1==dis2)
                        return 0;
                    else
                        return -1;
                }
            });
            int legal_count=0,illegal_count=0;
            for(int i=0;i<10;i++){
                if(((Test2Activity.Point)list.get(i)).user==1)
                    legal_count++;
                else
                    illegal_count++;
            }
            Log.d(TAG,"The number of legal point:"+Integer.toString(legal_count));
            if(legal_count>illegal_count-1)
                return true;
            else
                return false;
        }
        public boolean ONE_CLASS_KNN(Double[] features,final int normal_type,final int tester){
            Log.d(TAG,"One class knn is used");
            switch(normal_type){
                case Z_SCORE:  features=z_score(features);break;
                case MEAN: features=mean_normal(features);break;
                case SUM:    features=sum_normal(features);break;
                case SD: features=sd_normal(features);break;
                default:;
            }
            ArrayList list=new ArrayList();
            Log.d(TAG,"ave:"+ave_distance);
            Log.d(TAG,"max:"+max_distance);
            int user_count=0;
            double ave_dis=0;
            for(int i=0;i<data_num;i++){
                ave_dis+=getDis(features,values[i]);
                user_count++;
            }
            ave_dis/=user_count;
            Log.d(TAG,"ave_dis:"+ave_dis);
            if(tester==LEGAL){
                if(ave_dis<ave_distance+(max_distance-ave_distance)/0.3  ) {
                    return true;
                }else{
                    return false;
                }
            }else{
                if(ave_dis<ave_distance+(max_distance-ave_distance)/6  ) {
                    return true;
                }else{
                    return false;
                }
            }


        }

        /*
    2019/10/14 调整1.2.3.6.7.8特征比重，降低其他比重
 */
        public double getDis(Double[] p1,double[] p2){
            double dis=0;
            for(int i=0;i<FEATURE_NUM;i++){
                switch(i+1) {
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                    case 6:
                    case 7:
                    case 8:
                        dis += 1.2 * Math.pow(p1[i] - p2[i], 2);
                    default:
                        dis += 0.8 * Math.pow(p1[i] - p2[i], 2);
                }
            }
            dis=Math.sqrt(dis);
            return dis;
        }
        private Double[] sd_normal(Double[] feature){
            double[] sd=TrainPresenter.sd;
            for(int i=0;i<FEATURE_NUM;i++){
                feature[i]=feature[i]/sd[i];
            }
            return feature;
        }
        private Double[] sum_normal(Double[] feature){
            double[] sum=TrainPresenter.sum;
            for(int i=0;i<FEATURE_NUM;i++){
                feature[i]=feature[i]/sum[i];
            }
            return feature;
        }
        private Double[] mean_normal(Double[] feature){
            double[] max=TrainPresenter.max;
            double[] min=TrainPresenter.min;
            for(int i=0;i<FEATURE_NUM;i++){
                feature[i]=(feature[i]-min[i])/(max[i]-min[i]);
            }
            return feature;
        }
        private Double[] z_score(Double[] feature){
            double[] variance=TrainPresenter.variance;
            double[] average=TrainPresenter.average;
            for(int i=0;i<FEATURE_NUM;i++){
                feature[i]=(feature[i]-average[i])/variance[i];
            }
            return feature;
        }

        /**
         * @Title: onProgressUpdate
        　　* @Description: 根据publishProgress方法传入的value值更新UI线程
        　　* @param [values]
        　　* @return void
        　　* @throws
        　　*/
        protected void publishProgress(boolean value) {
            if (value)
                mLockView.unlockSuccess();
            else mLockView.unlockFail();
        }
    }
}
