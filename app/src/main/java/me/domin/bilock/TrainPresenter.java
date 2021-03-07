package me.domin.bilock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.administrator.mfcc.MFCC;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import permissions.dispatcher.NeedsPermission;

import static android.content.ContentValues.TAG;

/**
 　　* @ClassName: TrainPresenter
 　　* @Description: 处理TrainActivity的逻辑业务，实现录音、获取训练样本的功能
 　　* @author Administrator
 　　*/
public class TrainPresenter implements TrainContract.Presenter{

    TrainContract.View view;

    public final String TAG="TrainPresenter";
    public final int USER=1;
    public int type=0;

    public static final int NONE=0;
    public static final int Z_SCORE=1;
    public static final int SUM=2;
    public static final int MEAN=3;
    public static final int SD=4;
    public static final int MAX=5;

    static double[][] values;
    static int[] user;
    static int count;

    public TrainPresenter(TrainContract.View trainView){
        this.view=trainView;
    }
    public AudioRecord record;
    int sampleRate = 44100;
    int fftlen = 1024;
    int minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
    int numOfReadShort;
    int readChunkSize = fftlen;  // Every hopLen one fft result (overlapped analyze window)
    short[] audioSamples = new short[readChunkSize];
    int bufferSampleSize = Math.max(minBytes / 2, fftlen / 2) * 2;
    final static int FEATURE_NUM=MFCC.LEN_MELREC;

    @SuppressLint("SetTextI18n")
    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO})
    /**
        * @Title: trainData
    　　* @Description: 初始化录音器，开启一个线程池，执行RecordTask录音任务。但世界上只用了1个线程
    　　* @param []
    　　* @return void
    　　*/
    public void trainData(int type) {
        //权限
        this.type=type;
        initRecorder();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        executor.execute(new RecordTask());
    }
    @NeedsPermission({Manifest.permission.RECORD_AUDIO})
    /**
        * @Title: initRecorder
    　　* @Description: 初始化录音器，设置采样值等信息
    　　* @param []
    　　* @return void
    　　*/
    public void initRecorder() {
        record = new AudioRecord(6, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, 2 * bufferSampleSize);
        record.startRecording();
        bufferSampleSize = (int) Math.ceil(1.0 * sampleRate / bufferSampleSize) * bufferSampleSize;
    }

    /**
        * @Title: trainModel
    　　* @Description:  读取所有的特征值文件，整合到一起，记录数据，并计算样本之间距离的最大值和平均值
    　　* @param
    　　* @return
    　　* @throws
    　　*/
    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO})
    @Override
    public void trainModel(int normal_type) {
        BufferedOutputStream bw;
        BufferedInputStream br;
        File modelData=new File(FileUtil.getFilePathName(FileUtil.MODEL_DATA));
        File[] features=new File(FileUtil.getFilePathName(FileUtil.MODEL_PATH)).listFiles();
        byte[] buffer=new byte[10240];
        int length=0;

       values=new double[200][FEATURE_NUM];
       user=new int[200];
        count=0;
        double value;

        for(int i=0;i<FEATURE_NUM;i++){
            average[i]=0;
            max[i]=-9999;
            min[i]=9999;
            sum[i]=0;
            sd[i]=0;
        }

        try{
            bw=new BufferedOutputStream(new FileOutputStream(modelData));
            for(int j=0;j<features.length;j++) {
                File feature=features[j];
                if (feature.getName().contains("Feature")&&feature.getName().contains(".txt")&&!feature.getName().contains("ORIG")) {
                    br = new BufferedInputStream(new FileInputStream(feature));
//                    dis=new DataInputStream(new FileInputStream(feature));
//                    dis.readInt();
                    length=br.read(buffer);

                    String str=new String(buffer,0,length);
                    String[] strs=str.split(" ");
                    user[count]=Integer.parseInt(strs[0]);
                    for(int k=0;k<strs.length;k++){
                        String[] ss=strs[k].split(":");
                        if(ss.length>=2){
                            int index=Integer.parseInt(ss[0])-1;
                            double feature_value=Float.parseFloat(ss[1]);
                            values[count][index]=feature_value;
                            sum[index]+=feature_value;
                            sd[index]+=feature_value*feature_value;
                            max[index]=max[index]>feature_value?max[index]:feature_value;
                            min[index]=min[index]<feature_value?min[index]:feature_value;
                        }
                    }
                    count++;
                    //length = br.read(buffer, 0, 1024);
                    //bw.write(buffer, 0, length);
                    br.close();
                    //dis.close();
                }
            }

            for(int i=0;i<FEATURE_NUM;i++){
                average[i]=sum[i]/count;
                sd[i]=Math.sqrt(sd[i]);
            }

            switch(normal_type){
                case Z_SCORE:   values=z_score(values,average,count);break;
                case MEAN: values=mean_normal(values,min,max,count);break;
                case SUM:     values=sum_normal(values,sum,count);break;
                case SD: values=sd_normal(values,sd,count);break;
                case MAX: values=max_normal(values,max,count);break;
                default:;
            }

            getLimit();
            String s="average distance:"+ave_dis+"\r\n"+"max distance: "+max_dis+"\r\n";
            Log.d(TAG,s);
            bw.write(s.getBytes("UTF-8"));
            for(int i=1;i<=count;i++){
                StringBuilder sb=new StringBuilder();
                if(user[i-1]==1)
                    sb.append("+1");
                else
                    sb.append("-1");
                sb.append(" ");
                for(int j=1;j<=FEATURE_NUM;j++){
                    sb.append(j);
                    sb.append(":");
                    sb.append(values[i-1][j-1]);
                    sb.append(" ");
                }
                sb.append("\r\n");
                bw.write(sb.toString().getBytes("UTF-8"));
            }
            bw.flush();
            bw.close();

        }catch (IOException e){
            Log.e(TAG, "trainModel: error" );
        }
       /* try {
            //MFCC.svmTrain();

        }catch (IOException e){
            Log.e(TAG, "trainModel: error" );
        }*/

        //view.finishTrain();
    }

    public static double ave_dis=0,max_dis=0,min_dis=10000;
    /**
        * @Title: getLimit
    　　* @Description: 该方法用于计算合法样本中单个样本到其他样本的距离的平均值以及最大值，2019/10/14修改，提高部分特征值的比重。
    　　* @param
    　　* @return
    　　* @throws
    　　*/
    public void getLimit(){
        double[] distance=new double[count];

        for(int i=0;i<count;i++){
            for(int j=i+1;j<count;j++){
                distance[i]+=getDis(values[i],values[j]);
                distance[j]+=getDis(values[i],values[j]);
            }
            distance[i]/=count-1;
            ave_dis+=distance[i];
            max_dis=distance[i]>max_dis?distance[i]:max_dis;
            min_dis=distance[i]<min_dis?distance[i]:min_dis;
        }
        ave_dis/=count;

    }
    /*
        2019/10/14 调整1.2.3.6.7.8特征比重，降低其他比重
     */
    public double getDis(double[] p1,double[] p2){
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

    public void trainModel(int normal_type,double n) {
        BufferedOutputStream bw;
        BufferedInputStream br;
        File modelData=new File(FileUtil.getFilePathName(FileUtil.MODEL_DATA));
        File[] features=new File(FileUtil.getFilePathName(FileUtil.MODEL_PATH)).listFiles();
        byte[] buffer=new byte[10240];
        int length=0;

        values=new double[200][FEATURE_NUM];
        user=new int[200];
        count=0;
        double value;

        for(int i=0;i<FEATURE_NUM;i++){
            average[i]=0;
            max[i]=-9999;
            min[i]=9999;
            sum[i]=0;
            sd[i]=0;
        }

        try{
            bw=new BufferedOutputStream(new FileOutputStream(modelData));
            for(int j=0;j<features.length;j++) {
                File feature=features[j];
                if (feature.getName().contains("Feature")&&feature.getName().contains(".txt")&&!feature.getName().contains("ORIG")) {
                    br = new BufferedInputStream(new FileInputStream(feature));
//                    dis=new DataInputStream(new FileInputStream(feature));
//                    dis.readInt();
                    length=br.read(buffer);

                    String str=new String(buffer,0,length);
                    String[] strs=str.split(" ");
                    user[count]=Integer.parseInt(strs[0]);
                    for(int k=0;k<strs.length;k++){
                        String[] ss=strs[k].split(":");
                        if(ss.length>=2){
                            int index=Integer.parseInt(ss[0])-1;
                            double feature_value=Float.parseFloat(ss[1]);
                            values[count][index]=feature_value;
                            sum[index]+=feature_value;
                            sd[index]+=feature_value*feature_value;
                            max[index]=max[index]>feature_value?max[index]:feature_value;
                            min[index]=min[index]<feature_value?min[index]:feature_value;
                        }
                    }
                    count++;
                    //length = br.read(buffer, 0, 1024);
                    //bw.write(buffer, 0, length);
                    br.close();
                    //dis.close();
                }
            }
            for(int i=0;i<FEATURE_NUM;i++){
                average[i]=sum[i]/count;
                sd[i]=Math.sqrt(sd[i]);
            }
            switch(normal_type){
                case Z_SCORE:   values=z_score(values,average,count);break;
                case MEAN: values=mean_normal(values,min,max,count);break;
                case SUM:     values=sum_normal(values,sum,count);break;
                case SD: values=sd_normal(values,sd,count);break;
                default:;
            }


            for(int i=1;i<=count;i++){
                StringBuilder sb=new StringBuilder();
                if(user[i-1]==1)
                    sb.append("+1");
                else
                    sb.append("-1");
                sb.append(" ");
                for(int j=1;j<=FEATURE_NUM;j++){
                    sb.append(j);
                    sb.append(":");
                    sb.append(values[i-1][j-1]);
                    sb.append(" ");
                }
                sb.append("\r\n");
                bw.write(sb.toString().getBytes("UTF-8"));
            }
            bw.flush();
            bw.close();

        }catch (IOException e){
            Log.e(TAG, "trainModel: error" );
        }
        try {
            MFCC.svmTrain(n);
            //view.finishTrain();
        }catch (IOException e){
            Log.e(TAG, "trainModel: error" );
        }
    }


    static double[] variance=new double[FEATURE_NUM];
    static double[] average=new double[FEATURE_NUM];
    static double[] max=new double[FEATURE_NUM];
    static double[] min=new double[FEATURE_NUM];
    static double[] sum=new double[FEATURE_NUM];
    static double[] sd=new double[FEATURE_NUM];
    private double[][] sum_normal(double[][] values,double[] sum,int numOfSample){
        for(int i=0;i<numOfSample;i++){
            for(int j=0;j<FEATURE_NUM;j++){
                values[i][j]=values[i][j]/sum[j];
            }
        }
        return values;
    }
    private double[][] sd_normal(double[][] values,double[] sd,int numOfSample){
        for(int i=0;i<numOfSample;i++){
            for(int j=0;j<FEATURE_NUM;j++){
                values[i][j]=values[i][j]/sd[j];
            }
        }
        return values;
    }

    /**
        * @Title:    mean_normal  均值归一化
    　　* @Description: 对特征值进行均值归一化的处理，
    　　* @param
    　　* @return
    　　*/
    private double[][] mean_normal(double
                                           [][] values,double[] min,double[] max,int numOfSample){
        for(int i=0;i<numOfSample;i++){
            for(int j=0;j<FEATURE_NUM;j++){
                values[i][j]=(values[i][j]-min[j])/(max[j]-min[j]);
            }
        }
        return values;
    }
    /**
        * @Title: z_score
    　　* @Description: 对特征值进行z_score处理，即(value-average)/variance，以减少某些特征值带来的影响
    　　* @param
            values：特征值
            average：每个特征的均值
            count：声音样本的总数
    　　* @return    处理后的特征值
    　　*/
    private double[][] z_score(double[][] values,double[] average,int count){


        for(int i=0;i<count;i++){
            for(int j=0;j<FEATURE_NUM;j++){
                variance[j]+=Math.pow((values[i][j]-average[j]),2);
            }
        }
        for(int j=0;j<FEATURE_NUM;j++){
            variance[j]=(double)Math.sqrt((double)variance[j]/count);
        }
        for(int i=0;i<count;i++){
            for(int j=0;j<FEATURE_NUM;j++){
                values[i][j]=(values[i][j]-average[j])/variance[j];
            }
        }

        return values;
    }
    private double[][] max_normal(double[][] values,double[] max,int numOfSample){
        for(int i=0;i<numOfSample;i++){
            for(int j=0;j<FEATURE_NUM;j++){
                values[i][j]=values[i][j]/max[j];
            }
        }
        return values;
    }


    /**
     　　* @ClassName: RecordTask
     　　* @Description: 用于读取录音数据，用WavWriter处理数据，提取特征，得到10个声音样本
     　　* @author Administrator
     　　*/
    class RecordTask implements Runnable {
        WavWriter wavWriter = new WavWriter(WavWriter.MODEL, sampleRate);
        WavWriter shortWriter=new WavWriter(WavWriter.MODEL,sampleRate);

        @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO})
        @Override
        public void run() {
            wavWriter.start();
            int fileNumber = 0;

            //总共写10个声音文件
            while (fileNumber < 10) {

                int max = 0;
                int num = 0;
                //每个声音样本包括2个峰值段，由wavWriter记录
                while (num != 2) {
                    numOfReadShort = record.read(audioSamples, 0, readChunkSize);   // pulling
                    max = wavWriter.pushAudioShortNew(audioSamples, numOfReadShort);  // Maybe move this to another thread?
                    view.updateMax(max);
                    //max返回-1表示有提取峰值段
                    if (max == -1)
                        num++;
                }


                int[] signal = wavWriter.getSignal();
                BufferedWriter bw = null;
                //get average energy
                //double energy = getEnergy(signal);


                byte[] buffer = new byte[signal.length * 2];
                for (int i = 0; i < signal.length; i++) {
                    buffer[2 * i] = (byte) (signal[i] & 0xff);
                    buffer[2 * i + 1] = (byte) ((signal[i] >> 8) & 0xff);
                }


                wavWriter.list.clear();



                /*
                    修改日期：2019/6/5
                    修改内容：改变写入的文件路径
                    修改人：July
                 */
                //提取特征值
                /*
                    修改日期：2019/10/14
                    修改内容：将平均能量算入特征值之一
                 */
                /*
                修改日期：2019/12/4
                修改内容：去掉平均能量
                 */
                Double[] featureDouble = new Double[FEATURE_NUM];
                String path = FileUtil.getFilePathName(FileUtil.MODEL_RECORD);
                try {

                    System.arraycopy( MFCC.getFeatures(path, buffer, signal.length, 44100),0,featureDouble,0,MFCC.LEN_MELREC);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //获取刚记录的2个峰值段
               /* new Thread(){
                    @Override
                    public void run() {
                        super.run();*/
                shortWriter.setPathandStart(path.substring(0, path.indexOf(".txt")) + ".wav");
                shortWriter.write(signal, signal.length);
                shortWriter.stop();
               /*     }
                }.start();*/


                try {
                    bw = createBufferedWriter(FileUtil.getFilePathName(FileUtil.MODEL_FEATURE));
                } catch (IOException e) {
                    Log.e(TAG, "run: record error");
                }
                //将所有MFCC特征写入文件
                //将数据存入文件
                writeData(featureDouble, bw);

                fileNumber++;
                view.changeNum(fileNumber);
            }
            synchronized (this) {
                try {
                    new Thread().sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            record.stop();
            wavWriter.stop();
            record.release();
            trainModel(NONE);
            view.finishTrain();
        }
        /**
            * @Title: getEnergy
        　　* @Description: 算能量的平均值，并求以2为底的对数
        　　* @param        声音信号
        　　* @return        能量值
        　　* @throws
        　　*/
        private double getEnergy(int[] signal){
            double energy=0;
            for(int i=0;i<signal.length;i++){
                energy+=signal[i];
            }
            energy/=signal.length;
            energy=Math.abs(energy);
            energy=Math.log(energy)/Math.log(2);
            return energy;
        }
        private void writeData(Double feature[], BufferedWriter bw) {

            try {
                if(type==USER)
                    bw.write(1 + " ");
                else{
                    bw.write(-1+" ");
                }
                for (int i = 0; i < feature.length; i++) {
                    bw.write((i + 1) + ":" + String.valueOf(feature[i]));
                    if (i != feature.length - 1)
                        bw.write(" ");
                    else bw.write("\n");
//                Log.d(TAG, "writeModel: feature = " + feature[i]);
                }

                bw.write(" \n");
                bw.flush();

            } catch (IOException e) {
                Log.e(TAG, "writeData: ioexception" );
            }
        }
        /*
            修改日期：2019/6/5
            内容：改变参数
            修改人：July
         */
        private BufferedWriter createBufferedWriter(String name) throws IOException {
            File file = new File( name);
            if (!file.getParentFile().exists())
                file.getParentFile().mkdirs();
            return new BufferedWriter(new FileWriter(file));
        }
    }
}
