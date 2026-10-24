package gg.maxlevel.mlqwork;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    // MLQ on screen: its own pop-up shows new notices, so the phone's notification stays quiet
    public static volatile boolean inForeground = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MlqFiles.class);          // direct downloads to the phone's Downloads folder
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        inForeground = true;
    }

    @Override
    public void onPause() {
        inForeground = false;
        super.onPause();
    }
}
