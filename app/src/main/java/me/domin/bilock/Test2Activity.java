package me.domin.bilock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.administrator.mfcc.MFCC;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.ZeroCrossingRateProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import butterknife.BindView;
import butterknife.ButterKnife;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class Test2Activity extends AppCompatActivity implements TrainContract.View,LockContract.View{

    TrainPresenter mPresenter;
    LockPresenter lockPresenter;
    @BindView(R.id.bt_clear_all)
    Button bt_clear_all;

    public static final int NONE=0;
    public static final int Z_SCORE=1;
    public static final int SUM=2;
    public static final int MEAN=3;
    public static final int SD=4;
    public static final int MAX=5;
    public static final int LEGAL=10;
    public static final int ILLEGAL=11;

    private static final String TAG="Test2Activity";

    @BindView(R.id.bt_record)
    Button bt_record;
    @BindView(R.id.bt_clear_other)
    Button bt_clear_other;
    @BindView(R.id.bt_clear_user)
    Button bt_clear_user;
    @BindView(R.id.bt_train)
    Button bt_train;
    @BindView(R.id.bt_test)
    Button bt_test;
    @BindView(R.id.tv_data)
    TextView tv_data;
    @BindView(R.id.bt_other)
    Button bt_other;
    @BindView(R.id.bt_user_offline)
    Button bt_user_offline;
    @BindView(R.id.bt_other_offline)
    Button bt_other_offline;
    @BindView(R.id.bt_test_offline)
    Button bt_test_offline;
    @BindView(R.id.bt_sample)
    Button bt_sample;
    @BindView(R.id.et_min_noise)
    EditText et_min_noise;
    @BindView(R.id.bt_set)
    Button bt_set;
    @BindView(R.id.tv_max)
    TextView tv_max;

    static final public int CHANGE_NUM=0,MAX_NUM=1;
    static final public int USER=1,OTHER=2;
    static final public int TEST_FINISH=3;
    static final public int CHANGE_MAX=4;
    Handler handler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case CHANGE_NUM:
                    int num=(int)msg.obj;
                    toast("File number:"+num);
                    break;
                case MAX_NUM:
                    toast("train finish!");
                    bt_record.setEnabled(true);
                    bt_other.setEnabled(true);
                    break;
                case TEST_FINISH:
                    bt_test.setEnabled(true);
                    break;
                case CHANGE_MAX:
                    tv_max.setText("max:"+(int)msg.obj);

            }
        }
    };

    int right=0,wrong=0,total=0;
    double rightRate=0;

    @SuppressLint("SetTextI18n")
    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO})

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test2);
        mPresenter=new TrainPresenter(this);
        lockPresenter=new LockPresenter(this);
        ButterKnife.bind(this);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();


        bt_clear_all.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                File[] files=new File(FileUtil.getFilePathName(FileUtil.MODEL_PATH)).listFiles();
                for(File file:files){
                    file.delete();
                }
                toast("clear finished");
                right=wrong=total=0;
                rightRate=0;
            }
        });
        bt_clear_other.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File noise_train=new File(FileUtil.absolutePath+"/data/noise/train");
                File noise_test=new File(FileUtil.absolutePath+"/data/noise/test");
                for(File file:noise_test.listFiles()){
                    file.delete();
                }
                for(File file:noise_train.listFiles()){
                    file.delete();
                }
                toast("clear other sample");
            }
        });
        bt_clear_user.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File user_train=new File(FileUtil.absolutePath+"/data/user/train");
                File user_test=new File(FileUtil.absolutePath+"/data/user/test/");
                for(File file:user_test.listFiles()){
                    file.delete();
                }
                for(File file:user_train.listFiles()){
                    file.delete();
                }
                toast("clear user sample");
            }
        });
        bt_record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPresenter.trainData(USER);
                bt_record.setEnabled(false);
            }
        });
        bt_other.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPresenter.trainData(OTHER);
                bt_other.setEnabled(false);
            }
        });
        bt_train.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toast("start training!");
                mPresenter.trainModel(NONE);
                tv_data.setText("Ave:"+ TrainPresenter.ave_dis+" Max:"+TrainPresenter.max_dis);
            }
        });

        bt_test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toast("test start!");
                lockPresenter.initData();
                lockPresenter.startRecord(NONE,LEGAL);
                bt_test.setEnabled(false);
               // testForTest(USER,NONE);
            }
        });
        bt_user_offline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                trainForTest(USER);
            }
        });
        bt_other_offline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                trainForTest(OTHER);
            }
        });
        bt_test_offline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                train();
                //testForTest(OTHER,NONE);
            }
        });
        bt_sample.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPeak();
            }
        });
        bt_set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WavWriter.MIN_NOISE=Integer.parseInt(et_min_noise.getText().toString());
                toast("Min Volume:"+WavWriter.MIN_NOISE);
            }
        });
    }

    private void getPeak(){
        File source=new File(FileUtil.absolutePath+"/source/");
        FileInputStream fis=null;
        WavWriter wavWriter=new WavWriter(WavWriter.MODEL,44100);
        WavWriter peakWriter=new WavWriter(WavWriter.MODEL,44100);

        byte[] buffer=new byte[1024];
        int length=0;
        int file_index=0;
        File[] sounds=source.listFiles();
        wavWriter.start();
        for(File sound:sounds){
            file_index++;
            try{

                fis=new FileInputStream(sound);
                fis.skip(44);
                int max=0;
                int num=0;
                while((length=fis.read(buffer))!=-1&&num!=2){
                    short[] shorts=new short[(length+1)/2];
                    for(int i=0;i<length/2;i++) {
                        shorts[i] = (short) ((buffer[2 * i] & 0x000000FF) | ((int) (buffer[2 * i + 1]) << 8));
                    }
                    max=wavWriter.pushAudioShortNew(shorts,shorts.length);
                    if(max==-1){
                        num++;
                    }
                }

                if(num!=2){
                    wavWriter.list.clear();
                    continue;
                }

                int[] signal = wavWriter.getSignal();

                wavWriter.list.clear();



                String path=FileUtil.getFilePathName(FileUtil.MODEL_RECORD);

                if(signal.length>0) {
                    peakWriter.setPathandStart(path.substring(0, path.indexOf(".txt")) + file_index+".wav");
                    peakWriter.write(signal, signal.length);
                    peakWriter.stop();
                }

            }catch(IOException e){
                toast("IOException");
            }

        }
        wavWriter.stop();

    }

    private Double[] sd_normal(Double[] feature){
        double[] sd=TrainPresenter.sd;
        for(int i=0;i<MFCC.LEN_MELREC;i++){
            feature[i]=feature[i]/sd[i];
        }
        return feature;
    }
    private Double[] sum_normal(Double[] feature){
        double[] sum=TrainPresenter.sum;
        for(int i=0;i<MFCC.LEN_MELREC;i++){
            feature[i]=feature[i]/sum[i];
        }
        return feature;
    }
    private Double[] mean_normal(Double[] feature){
        double[] max=TrainPresenter.max;
        double[] min=TrainPresenter.min;
        for(int i=0;i<MFCC.LEN_MELREC;i++){
            feature[i]=(feature[i]-min[i])/(max[i]-min[i]);
        }
        return feature;
    }
    private Double[] z_score(Double[] feature){
        double[] variance=TrainPresenter.variance;
        double[] average=TrainPresenter.average;
        for(int i=0;i<MFCC.LEN_MELREC;i++){
            feature[i]=(feature[i]-average[i])/variance[i];
        }
        return feature;
    }
    private Double[] max_normal(Double[] feature){
        double[] max=TrainPresenter.max;
        for(int i=0;i<MFCC.LEN_MELREC;i++){
            feature[i]=feature[i]/max[i];
        }
        return feature;
    }
    public String preProcess(Double[] features) throws IOException{


        //features=z_score(features);
       //features=mean_normal(features);
//         features=sd_normal(features);
//        features=sum_normal(features);
        String filename=FileUtil.getFilePathName(FileUtil.TEST_FEATURE);
        File file=new File(filename);
        if(!file.exists())
            file.createNewFile();


        BufferedWriter bw=new BufferedWriter(new FileWriter(file));
        bw.write("1 ");
        for(int i=0;i<features.length;i++){
            bw.write(String.valueOf(i+1));
            bw.write(":"+features[i]+" ");
        }
        bw.flush();
        bw.close();
        return filename;
    }


    public void testForTest(){
        File parent=new File(FileUtil.getFilePathName(FileUtil.TEST_PATH));
        File[] testFeature=parent.listFiles();

        try{
            for(File feature:testFeature){
                if ((MFCC.svmPredict(feature.getAbsolutePath()) == 1)) {
                    //toast("Unlock successfully!");
                    right++;
                    total++;
                    updateData();
                } else {
                    //toast("Unlock Fail!");
                    wrong++;
                    total++;
                    updateData();
                }
            }
        }catch (IOException e){

        }
    }
    //用已有的模型测试/data/user/test 和 /data/noise/test两个文件夹中的声音文件（根据传入的类型选择一个），测试准确率
    @SuppressLint("NewApi")
    public void testForTest(int type,int normal_type,BufferedWriter bw){

        File parent=null;
        double max_count=0,min_count=100,ave_count=0;
        try {
            if(type==USER)
                parent=new File(FileUtil.absolutePath+"/data/user/test");
            else
                parent=new File(FileUtil.absolutePath+"/data/noise/test");
            File[] files=parent.listFiles();
            for(File file:files){
                if(file.getName().contains("Record")){
                    FileInputStream fis=null;
                    byte[] buffer=new byte[2048];
                    Double[] featureDouble=new Double[TrainPresenter.FEATURE_NUM];
                    int length=0;
                    int data_length=0;
                        try{
                            data_length=0;
                            fis = new FileInputStream(file);
                            fis.skip(44);
                            while((length=fis.read(buffer))!=-1){
                                for(int i=0;i<length;i=i+2){
                                    data_length++;
                                }
                            }

                            String path=FileUtil.getFilePathName(FileUtil.TEST_RECORD);
                            System.arraycopy(MFCC.getFeatures(path, buffer , data_length, 44100),0, featureDouble,0,MFCC.LEN_MELREC);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            double count=ONE_CLASS_KNN(featureDouble,normal_type);
                            max_count=count>max_count?count:max_count;
                            min_count=count<min_count?count:min_count;
                            ave_count+=count;
                            if(count<TrainPresenter.ave_dis+(TrainPresenter.max_dis-TrainPresenter.ave_dis)/1.5){
                                //toast("Unlock successfully!");
                                right++;
                                total++;
                               // updateData();
                            }else{
                                //toast("Unlock Fail!");
                                wrong++;
                                total++;
                                //updateData();
                            }
                          /*if(KNN(featureDouble,normal_type)){
                              //toast("Unlock successfully!");
                              right++;
                              total++;
                              // updateData();
                          }else{
                              //toast("Unlock Fail!");
                              wrong++;
                              total++;
                              //updateData();
                          }
                   /* String path=preProcess(featureDouble);
                    if ((MFCC.svmPredict(path) == 1)) {
                        //toast("Unlock successfully!");
                        right++;
                        total++;
                        updateData();
                    } else {
                        //toast("Unlock Fail!");
                        wrong++;
                        total++;
                        updateData();
                    }*/
                }
            }
            updateData();
            ave_count/=files.length;
            bw.write("Type:"+type+"; min:"+min_count+"; max:"+max_count+"; average:"+ave_count+"\r\n");
            Log.d(TAG,"Type:"+type+"; min:"+min_count+"; max:"+max_count+"; average:"+ave_count);

        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG,e.getLocalizedMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean KNN(Double[] features,final int normal_type){
        switch(normal_type){
            case Z_SCORE:  features=z_score(features);break;
            case MEAN: features=mean_normal(features);break;
            case SUM:    features=sum_normal(features);break;
            case SD: features=sd_normal(features);break;
            case MAX: features=max_normal(features);break;
            default:;
        }
        double[][] values=TrainPresenter.values;
        int[] user=TrainPresenter.user;
        ArrayList list=new ArrayList();
        double avg_distance=0;
        int user_count=0;
        for(int i=0;i<TrainPresenter.count;i++){
            Point p=new Point(values[i],user[i]);
            p.calDis(features);
            list.add(p);
            if(user[i]==1){
                avg_distance+=p.distance;
                user_count++;
            }
        }
        avg_distance/=user_count;

        list.sort(new Comparator() {
            @Override
            public int compare(Object o, Object t1) {
                double dis1=((Point)o).distance;
                double dis2=((Point)t1).distance;
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
            if(((Point)list.get(i)).user==1)
                legal_count++;
            else
                illegal_count++;
        }
        if(legal_count>=illegal_count)
            return true;
        else
            return false;

       // return avg_distance;
    }

    /*
    返回到样本点的平均距离
     */
    public double ONE_CLASS_KNN(Double[] features,final int normal_type){
        switch(normal_type){
            case Z_SCORE:  features=z_score(features);break;
            case MEAN: features=mean_normal(features);break;
            case SUM:    features=sum_normal(features);break;
            case SD: features=sd_normal(features);break;
            case MAX:features=max_normal(features);break;
            default:;
        }
        double[][] values=TrainPresenter.values;
        int[] user=TrainPresenter.user;
        double max_dis=TrainPresenter.max_dis,mid_dis=TrainPresenter.ave_dis;
        ArrayList list=new ArrayList();
        double avg_distance=0;
        int user_count=0;
        for(int i=0;i<TrainPresenter.count;i++){
            Point p=new Point(values[i],user[i]);
            p.calDis(features);
            list.add(p);
            if(user[i]==1){
                avg_distance+=p.distance;
                user_count++;
            }
        }
        avg_distance/=user_count;

        return avg_distance;
        /*if(avg_distance<mid_dis+(max_dis-mid_dis)/3){
            return true;
        }else{
            return false;
        }*/
    }

    /*
        修改:修改计算距离的方法，提高特征1.2.3.6.7.8的比重，降低其他比重
     */
    static public class Point{
        double[] values;
        int user;
        double distance;
        public Point(double[] values,int user){
            this.values=values;
            this.user=user;
        }
        public void calDis(Double[] features){
            distance=0;
            for(int i=0;i<TrainPresenter.FEATURE_NUM;i++){
                switch(i+1){
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                    case 6:
                    case 7:
                    case 8:
                        distance+=1.2*Math.pow(features[i]-values[i],2);
                    default :
                            distance+=0.8*Math.pow(features[i]-values[i],2);
                }
               /*distance+=Math.pow(features[i]-values[i],2);*/
            }
            distance=Math.sqrt(distance);
        }
    }

    /**
        * @Title: train
    　　* @Description: 测试不同数据处理方法以及不同训练样本数对准确率的影响
    　　* @param
    　　* @return
    　　*/
    public void train(){
        File user_train=new File(FileUtil.absolutePath+"/data/user/train");
        File user_test=new File(FileUtil.absolutePath+"/data/user/test/");
        File noise_train=new File(FileUtil.absolutePath+"/data/noise/train");
        File noise_test=new File(FileUtil.absolutePath+"/data/noise/test");
        File result=new File(FileUtil.absolutePath+FileUtil.getTime()+"result.txt");
        BufferedWriter bw=null;
        try{
            if(!result.exists())
                result.createNewFile();
          bw=new BufferedWriter(new FileWriter(result));

        Random random=new Random();
        //设置上下限
        int user_down_limit=10;
        int user_up_limit=10;
        int noise_down_limit=0;
        int noise_up_limit=0;
        int interval=1;
        //初始样本数都为10，处理方法为不作处理
        int user_sample_num;
        int noise_sample_num;
        int normal_type=Z_SCORE;



        //不同处理方法
       // for(normal_type=NONE;normal_type<=SD;normal_type++){

            //保证初始的合法训练样本数为user_down_limit
            user_sample_num=user_down_limit;
            File[] user_sample=user_train.listFiles();
            if(user_sample.length>user_sample_num) {
                do {
                    user_sample = user_train.listFiles();
                    File file = user_sample[random.nextInt(user_sample.length)];
                    file.renameTo(new File(user_test, file.getName()));
                } while (user_sample.length > user_sample_num);
            }else if(user_sample.length<user_sample_num){
                do {
                    user_sample = user_test.listFiles();
                    File file = user_sample[random.nextInt(user_sample.length)];
                    file.renameTo(new File(user_train, file.getName()));
                } while (user_train.listFiles().length < user_sample_num);
            }

            //样本数down_limit-up_limit
            for(;user_sample_num<=user_up_limit;){
                noise_sample_num=noise_down_limit;

                //to ensure the number of noise samples
                File[] noise_sample=noise_train.listFiles();
                if(noise_sample.length>noise_sample_num) {
                    do {
                        noise_sample = noise_train.listFiles();
                        File file = noise_sample[random.nextInt(noise_sample.length)];
                        file.renameTo(new File(noise_test, file.getName()));
                    } while (noise_sample.length > noise_sample_num);
                }else if(noise_sample.length<noise_sample_num){
                    do {
                        noise_sample = noise_test.listFiles();
                        File file = noise_sample[random.nextInt(noise_sample.length)];
                        file.renameTo(new File(noise_train, file.getName()));
                    } while (noise_train.listFiles().length < noise_sample_num);
                }



                for(;noise_sample_num<=noise_up_limit;){
 //               for(int j=0;j<=10;j++){
                    //to ensure the number of user train sample
                    user_sample=user_train.listFiles();
                    if(user_sample.length>user_sample_num) {
                        do {
                            user_sample = user_train.listFiles();
                            File file = user_sample[random.nextInt(user_sample.length)];
                            file.renameTo(new File(user_test, file.getName()));
                        } while (user_sample.length > user_sample_num);
                    }else if(user_sample.length<user_sample_num){
                        do {
                            user_sample = user_test.listFiles();
                            File file = user_sample[random.nextInt(user_sample.length)];
                            file.renameTo(new File(user_train, file.getName()));
                        } while (user_train.listFiles().length < user_sample_num);
                    }
                    clearFile();
                    trainForTest(USER);
                    //trainForTest(OTHER);
                    mPresenter.trainModel(normal_type);

                    bw.write("legal sample:"+user_sample_num+",illegal sample:"+noise_sample_num+";");
                    bw.write("ave_dis:"+TrainPresenter.ave_dis+";  max_dis:"+TrainPresenter.max_dis+";\r\n");

                    testForTest(USER,normal_type,bw);
                    rightRate=(double)(right)/(double)total;
                    double legal_rightRate=rightRate;

                    bw.write("legal: right num:"+right+",wrong num:"+wrong+",accuracy:"+String.valueOf(rightRate)+"\r\n");

                    right=wrong=0;
                    rightRate=0;
                    total=0;
                    testForTest(OTHER,normal_type,bw);
                    rightRate=(double)(wrong)/(double)total;
                    double illegal_rightRate=rightRate;

                    bw.write(" illegal: right num:"+right+",wrong num:"+wrong+",accuracy:"+String.valueOf(rightRate)+"\r\n");
                    bw.write("\r\n");
                    bw.flush();


                    for(File file:user_train.listFiles()){
                        file.renameTo(new File(user_test,file.getName()));
                    }

                    noise_sample_num+=interval;
                    noise_sample=noise_test.listFiles();
                    int i=interval;
                    //将测试样本随机n个移动到训练样本中
                    /*while(i>0){
                        File file=noise_sample[random.nextInt(noise_sample.length)];
                        if(file.getParent().contains("test")){
                            file.renameTo(new File(noise_train,file.getName()));
                            i--;
                        }
                    }*/
                }
                //将所有测试样本移动到训练样本中，随机留下几个
              /*  File[] noise=noise_train.listFiles();
                for(int k=noise_down_limit;k<noise.length;k++){
                    File file=noise[k];
                    file.renameTo(new File(noise_test,file.getName()));
                }*/
                user_sample_num +=interval;
                user_sample=user_test.listFiles();
                if(user_sample.length==0)
                    break;
                //将interval个合法测试样本移到训练样本中
                int i=interval;
                while(i>0){
                    File file=user_sample[random.nextInt(user_sample.length)];
                    if(file.getParent().contains("test")){
                        file.renameTo(new File(user_train,file.getName()));
                        i--;
                    }
                }
            }
       // }

            //更新手机中的目录
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri uri=Uri.fromFile(result);
            intent.setData(uri);
            getApplicationContext().sendBroadcast(intent);

            bw.close();
        }catch (IOException e){
            Log.e(TAG, "train: "+e.getMessage() );
        }
    }
    public void clearFile(){
        File[] files=new File(FileUtil.getFilePathName(FileUtil.MODEL_PATH)).listFiles();
        File[] testFile=new File(FileUtil.getFilePathName(FileUtil.TEST_PATH)).listFiles();
        for(File file:files){
            file.delete();
        }
        for(File file:testFile){
            file.delete();
        }
        toast("clear finished");
        right=wrong=total=0;
        rightRate=0;
    }
    public void trainForTest(int type){
        File parent=null;
        if(type==USER){
            parent=new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/Bilock/data/user/train");
        }else
            parent=new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/Bilock/data/noise/train");
        File[] files=parent.listFiles();

        ByteArrayOutputStream bos=new ByteArrayOutputStream();
        FileInputStream fis=null;
        byte[] buffer=new byte[2048];
        double[] data=new double[300000];
        Double[] featureDouble = null;
        int length=0;
        int data_length=0;
        for(File file:files){
            try {
                data_length=0;
                fis = new FileInputStream(file);
                fis.skip(44);
                while((length=fis.read(buffer))!=-1){
                    for(int i=0;i<length;i=i+2){
                        data_length++;
                    }
                }


                featureDouble=new Double[TrainPresenter.FEATURE_NUM];
                String path=FileUtil.getFilePathName(FileUtil.MODEL_RECORD);
                try {
                    System.arraycopy(MFCC.getFeatures(path, buffer , data_length, 44100),0,featureDouble,0,MFCC.LEN_MELREC);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                BufferedWriter bw=null;
                try {
                    bw = new BufferedWriter(new FileWriter(FileUtil.getFilePathName(FileUtil.MODEL_FEATURE)));
                } catch (IOException e) {
                    Log.e(TAG, "run: record error");
                }
                //将所有MFCC特征写入文件
                //将数据存入文件
                try {
                    if(type==USER)
                        bw.write(1 + " ");
                    else{
                        bw.write(-1+" ");
                    }
                    for (int i = 0; i < featureDouble.length; i++) {
                        bw.write((i + 1) + ":" + String.valueOf(featureDouble[i]));
                        if (i != featureDouble.length - 1)
                            bw.write(" ");
                        else
                            bw.write("\n");
//                Log.d(TAG, "writeModel: feature = " + feature[i]);
                    }

                    bw.write(" \n");
                    bw.flush();

                } catch (IOException e) {
                    Log.e(TAG, "writeData: ioexception" );
                }
            }catch (IOException e){
                Log.e(TAG, "trainForTest: error while reading wav files");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
      //  lockPresenter.initRecorder();
       // lockPresenter.currentRecordTaskNew();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    public void toast(String str){
        Toast.makeText(getApplicationContext(), str,Toast.LENGTH_SHORT).show();
    }

    @Override
    public void changeNum(int fileNum) {
        Message message=new Message();
        message.what=CHANGE_NUM;
        message.obj=fileNum;
        handler.sendMessage(message);
    }


    @Override
    public void finishTrain() {
            handler.sendEmptyMessage(MAX_NUM);

    }

    @Override
    public void updateMax(int max) {
        Message message=new Message();
        message.what=CHANGE_MAX;
        message.obj=max;
        handler.sendMessage(message);
    }

    @Override
    public void clear() {

    }

    @Override
    public InputStream getInputStream(String fileName) {
        return null;
    }

    @Override
    public void unlockSuccess() {
        toast("Unlock successfully!");
        right++;
        total++;
        updateData();
        lockPresenter.stopRecord();
        handler.sendEmptyMessage(TEST_FINISH);
    }

    @Override
    public void unlockFail() {
        toast("Unlock Fail!");
        wrong++;
        total++;
        updateData();
        lockPresenter.stopRecord();
        handler.sendEmptyMessage(TEST_FINISH);

    }
    public void updateData(){
        rightRate=(double)right/total;
        if(tv_data.getText().length()>100)
            tv_data.setText("");
        tv_data.setText(tv_data.getText()+"\r\nTotal: "+total+" \r\n Right:"+right+"\r\n Wrong: "+wrong+"\r\n Correct rate: "+rightRate*100+"%");
    }
}
