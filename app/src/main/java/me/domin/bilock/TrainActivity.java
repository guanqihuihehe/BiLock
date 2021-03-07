package me.domin.bilock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.administrator.mfcc.MFCC;
import com.hanks.htextview.base.AnimationListener;
import com.hanks.htextview.base.HTextView;
import com.hanks.htextview.scale.ScaleText;
import com.race604.drawable.wave.WaveDrawable;
import com.robinhood.ticker.TickerUtils;
import com.robinhood.ticker.TickerView;
import com.sackcentury.shinebuttonlib.ShineButton;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindDrawable;
import butterknife.BindView;
import butterknife.ButterKnife;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

import static me.domin.bilock.TrainPresenter.NONE;

@RuntimePermissions
public class TrainActivity extends AppCompatActivity implements TrainContract.View{

    private static final int NUM_CHANGE = 1;
    private static final int NUM_MAX = 2;
    static final public int USER=1,OTHER=2;
    TrainPresenter mPresenter;

    @BindView(R.id.finish)
    ShineButton shineButton;

    @BindView(R.id.image)
    ImageView imageView;

    @BindView(R.id.hint_text)
    TextView hintText;

    @BindView(R.id.finish_text)
    TextView finishText;

    WaveDrawable waveDrawable;

    @BindView(R.id.tickerView)
    TickerView tickerView;


    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case NUM_CHANGE:
                    int fileNum=(int)message.obj;
                    double num = getRandNumber(fileNum);
                    if(num>100){
                        num = 100.00;
                        shineButton.setVisibility(View.VISIBLE);
                        shineButton.performClick();
                        hintText.setText("完成声纹录制！");
                        tickerView.setText(num + "%");
                        waveDrawable.setLevel(fileNum * 1000);

                    } else {
                        tickerView.setText(num + "%");
                        waveDrawable.setLevel(fileNum * 1000);
                    }
                    break;
                case NUM_MAX:
                    Intent intent = new Intent(TrainActivity.this, LockScreenActivity.class);
                    startActivity(intent);
                    onStop();
                    finish();
                    break;
            }
        }
    };

    //随机产生一个10以内的数值，提升用户体验
    double getRandNumber(int num) {
        int rand = (int) (Math.random() * 900);
        double result = num * 10 + (double) rand / 100;
        return result;
    }

    @SuppressLint("SetTextI18n")
    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_train);

        mPresenter=new TrainPresenter(this);
        ButterKnife.bind(this);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();


        shineButton.init(this);
        tickerView.setCharacterLists(TickerUtils.provideNumberList());

        //初始化头像动画
        waveDrawable = new WaveDrawable(getDrawable(R.drawable.bilock_logo));
        imageView.setImageDrawable(waveDrawable);
        waveDrawable.setWaveSpeed(10);
        waveDrawable.setWaveAmplitude(10);

        //初始化数值动画
        tickerView.setText("0%");
        tickerView.setGravity(Gravity.START);
        hintText.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        hintText.getPaint().setFakeBoldText(true);
        finishText.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        finishText.getPaint().setFakeBoldText(true);

        mPresenter.trainData(USER);


    }

    /**
        * @Title: changeNum
    　　* @Description: 更新进度数值，由于动画不能够在子线程更新，此处仍保留用handler传递消息
    　　* @param  fileNum
    　　* @return
    　　*/
    public void changeNum(int fileNum) {
        Message message=new Message();
        message.what=NUM_CHANGE;
        message.obj=fileNum;
        handler.sendMessage(message);
    }
    /**
        * @Title: finishTrain
    　　* @Description: 通知TrainActivity训练完成，保留Handler，理由同上
    　　* @param
    　　* @return
    　　*/
    public void finishTrain(){
       handler.sendEmptyMessage(NUM_MAX);
    }

    @Override
    public void updateMax(int max) {

    }

}
