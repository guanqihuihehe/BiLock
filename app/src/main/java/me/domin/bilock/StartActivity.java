package me.domin.bilock;

import android.Manifest;
import android.app.Activity;
import android.app.TaskStackBuilder;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.stephentuso.welcome.WelcomeHelper;   //开场动画

import java.io.File;

import me.wangyuwei.particleview.ParticleView;    //开场动画

/*
    修改时间：2019/6/8
    修改人：July
    修改内容：修改主目录路径
 */
public class StartActivity extends AppCompatActivity {

    private static final String TAG = "StartActivity";
    private WelcomeHelper welcomeHelper;
    private ParticleView particleView;


    String path = FileUtil.getUserPath();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        final Activity activity = this;

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();
//
        particleView = findViewById(R.id.particle);
        particleView.startAnim();
//
        particleView.setOnParticleAnimListener(new ParticleView.ParticleAnimListener() {
            @Override
            public void onAnimationEnd() {
                welcomeHelper = new WelcomeHelper(activity, WelcomePage.class);
                welcomeHelper.show(savedInstanceState);
                welcomeHelper.forceShow();

            }
        });
        if(ContextCompat.checkSelfPermission(StartActivity.this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(StartActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        File[] files = new File(path).listFiles();
        if (files != null && files.length != 0)
        {
            startLockScreenActivity();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 1:
                if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    if(ContextCompat.checkSelfPermission(StartActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(StartActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    }
                }else{
                    Toast.makeText(this,"You denied the permission",Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    /**
        * @Title: onActivityResult
    　　* @Description: Activity结束时会调用的方法，此处在结束时开启TeachActivity
    　　* @param [requestCode, resultCode, data]
    　　* @return void
    　　* @throws
    　　*/
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        startTeachActivity();
    }

    void startLockScreenActivity()
    {
        Intent intent = new Intent(StartActivity.this, LockScreenActivity.class);
        startActivity(intent);
//        onDestroy();
        finish();
    }

    void startTeachActivity()
    {
        Intent intent = new Intent(StartActivity.this, TeachActivity.class);
        startActivity(intent);
        finish();
//        onDestroy();

    }


}
