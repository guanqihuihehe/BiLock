package com.example.administrator.mfcc;

import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.google.corp.productivity.specialprojects.android.fft.RealDoubleFFT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.ZeroCrossingRateProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;

import uk.me.berndporr.iirj.Butterworth;
//import be.tarsos.dsp.io.android.AudioDispatcherFactory;

import static android.content.ContentValues.TAG;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.floor;
import static java.lang.Math.log;
import static java.lang.Math.log10;
import static java.lang.Math.pow;


/**
    * 修改日志：
 * @date： 2019/7/31
 * @content：修改了dictionarypath，订正了mfcc传入的samplesize数值
 *
　　*/
public class MFCC {
    private static int FRAMES_PER_BUFFER = 512;           //帧长？海明窗大小
    private static int NOT_OVERLAP;                 //每帧的样本数
    private static final int NUM_FILTER = 50;       //滤波器的数目
    private static final int LEN_SPECTRUM = 2048;   //2的k次幂，与每帧的样本数最接近,输入FFT的数据大小？
    public static final int LEN_MELREC = 13;       //特征维度
    private static final double PI = 3.1415926;
    private static String pathName = Environment.getExternalStorageDirectory().getAbsolutePath()+"/Bilock/";
    private static String dictionaryPath =Environment.getExternalStorageDirectory().getAbsolutePath()+"/Bilock/user/";


    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    public static Double[] getFeatures(String absolutePath, byte[] bufferByte, int length, int i) throws IOException {
        int sampleRate = 44100;
        int bufferSize = 1024;
        int bufferOverlap = 512;
        //声音的采样精度
        int sampleSizeInBits = 16;
        File file = new File(absolutePath);
        File fileBefore=new File(file.getParent(),"ORIG_"+file.getName());
        file.getParentFile().mkdirs();

        //butterworth filter

        //预加重
/*        double[] preemp = new double[length];
        preemp[0] = bufferDouble[0];

        for (int j = 1; j < length; j++)
            preemp[j] = bufferDouble[j] - 0.95 * bufferDouble[j - 1];

        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
//        DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));
//        for (double u : bufferDouble
//                ) {
//            dos.writeDouble(u);
//            dos.writeUTF(",");
//        }
        for (double u : preemp
                ) {
            bw.write(String.valueOf(u));
            bw.write(",");
        }
        bw.flush();
        bw.close();*/

        ByteArrayInputStream bis=new ByteArrayInputStream(bufferByte,0,length*2);

        // InputStream inStream = new FileInputStream(absolutePath);
//        final float[] floatBuffer = TestUtilities.audioBufferSine();
//        final AudioDispatcher dispatcher = AudioDispatcherFactory.fromFloatArray(floatBuffer, sampleRate, bufferSize, bufferOverlap);
        AudioDispatcher dispatcher = new AudioDispatcher(new UniversalAudioInputStream(bis, new TarsosDSPAudioFormat(sampleRate, sampleSizeInBits, 1, true, false)), bufferSize, bufferOverlap);
        final be.tarsos.dsp.mfcc.MFCC mfcc = new be.tarsos.dsp.mfcc.MFCC(bufferSize, sampleRate, 13, 26, 300, 3000,20);
        final ZeroCrossingRateProcessor zcr=new ZeroCrossingRateProcessor();
        final FeatureProcessor featureProcessor=new FeatureProcessor(bufferSize,60,120);
        dispatcher.addAudioProcessor(mfcc);
        dispatcher.addAudioProcessor(new AudioProcessor() {

            @Override
            public void processingFinished() {
            }

            @Override
            public boolean process(be.tarsos.dsp.AudioEvent audioEvent) {
                return true;
            }
        });
        dispatcher.addAudioProcessor(zcr);
        dispatcher.addAudioProcessor(featureProcessor);
        dispatcher.run();

        float[] features = new float[LEN_MELREC];
        System.arraycopy(mfcc.getMFCC(),0,features,0,mfcc.getMFCC().length);
       /*features[13]=zcr.getZeroCrossingRate();
        features[14]=featureProcessor.getBandWidth();
        features[15]=featureProcessor.getEntropy();
        features[16]=featureProcessor.getATR();
        features[17]=featureProcessor.getAMR();
      /* features[0]=zcr.getZeroCrossingRate();
        features[1]=featureProcessor.getBandWidth();
        features[2]=featureProcessor.getEntropy();
        features[3]=featureProcessor.getATR();
        features[4]=featureProcessor.getAMR();*/
        Double featureDouble[] = new Double[features.length];
        for (int j = 0; j < featureDouble.length; j++) {
            featureDouble[j] = (double) features[j];
        }
        return featureDouble;
    }
    public static void svmTrain() throws IOException {

        svm_train train = new svm_train();
               String trainArgs[] = new String[]{"-s","2",dictionaryPath + "MFCCs_model.txt", dictionaryPath + "svm_model.txt"};
       // String trainArgs[] = new String[]{dictionaryPath + "MFCCs_model.txt", dictionaryPath + "svm_model.txt"};
       /* String scaleArgs[]=new String[]{"-s",dictionaryPath+"MFCCs_scale.txt",dictionaryPath+"MFCCs_model.txt",dictionaryPath+"MFCCs_waht"};
        svm_scale.main(scaleArgs);*/
//        String scaleArgs[] = new String[]{"-s", dictionaryPath + "model_one.txt" , "-l ", "-1", "-u ", "1", dictionaryPath + "iris.txt"};
//        String trainArgs[] = new String[]{"-s", "2", fileName + "model_one.txt", fileName + "data.model"};
//        String predictArgs[] = new String[]{"-b", "0", fileName + "text.txt", fileName + "data.model", fileName + "result.txt"};

//        scale.main(scaleArgs);
        svm_train.main(trainArgs);
        //查看是否出错
//        Log.i(TAG, "LibSvm: " + train.error_msg);

        //2019/9/28 加入svm——scale做尝试

    }
    public static void svmTrain(double n) throws IOException {

        svm_train train = new svm_train();
        String trainArgs[] = new String[]{"-s","2","-n",Double.toString(n),dictionaryPath + "MFCCs_model.txt", dictionaryPath + "svm_model.txt"};
        svm_train.main(trainArgs);
        //查看是否出错
//        Log.i(TAG, "LibSvm: " + train.error_msg);

        //2019/9/28 加入svm——scale做尝试

    }

    public static double svmPredict(String testFile) throws IOException {

        String predictArgs[] = new String[]{"-b", "0", testFile, dictionaryPath + "svm_model.txt", dictionaryPath + "result.txt"};
//        String predictArgs2[] = new String[]{"-b", "0", dictionaryPath + "model.txt", dictionaryPath + "data_model.txt", dictionaryPath + "result.txt"};
        //String scaleArgs[] =new String[]{"-r",dictionaryPath+"MFCCs_scale.txt",};
        double accuracy=0;
        accuracy=svm_predict.main(predictArgs);
        Log.d(TAG,"LibSvm:predict_label = "+accuracy);
        BufferedReader br=new BufferedReader(new FileReader(dictionaryPath+"result.txt"));
        String arg=br.readLine();
        accuracy=Double.parseDouble(arg);

//        double accuracy2 = svm_predict.main(predictArgs2);
//        Log.d(TAG, "LibSvm: accuracy = " + accuracy2);
//        Log.d(TAG, "LibSvm: accuracy2 = " + accuracy2);

        if (accuracy == 1)
            Log.e(TAG, "LibSvm: accuracy = " + accuracy);
        else Log.d(TAG, "LibSvm: accuracy = " + accuracy);


        return accuracy;
    }





}
