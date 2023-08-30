package com.sawthandar.faceregonition;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.widget.Button;

public class SplashActivity extends Activity {

    private Handler mHandler = new Handler();

    private Button goToFaceScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        int mCurrentOrientation = getResources().getConfiguration().orientation;

        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.activity_splash);
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_splash);
        }

        goToFaceScan = (Button) findViewById(R.id.go_to_face_scan_btn);

        goToFaceScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                SplashActivity.this.finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
