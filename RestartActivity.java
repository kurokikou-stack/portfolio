package com.windnauts.gvidas;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.contentcapture.DataShareWriteAdapter;

public class RestartActivity extends Activity {
    public static final String EXTRA_MAIN_PID = "RestartActivity.main_pid";
    private String ref;

    /*
    public static Intent createIntent(Context context, String ref_) {
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), RestartActivity.class.getName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // メインプロセスの PID を Intent に保存しておく
        intent.putExtra(RestartActivity.EXTRA_MAIN_PID, android.os.Process.myPid());
        intent.putExtra("hogehoge",ref_);
        Log.d("ref",ref_);
        return intent;
    }
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. メインプロセスを Kill する
        Intent intent = getIntent();
        int mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1);
        ref = intent.getStringExtra("hogehoge");
        Log.d("ref",ref);
        android.os.Process.killProcess(mainPid);

        // 2. MainActivity を再起動する
        Context context = getApplicationContext();
        Intent restartIntent = new Intent(Intent.ACTION_MAIN);
        restartIntent.setClassName(context.getPackageName(), MainActivity.class.getName());
        restartIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        restartIntent.putExtra("hoge",ref);
        context.startActivity(restartIntent);

        // 3. RestartActivity を終了する
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
