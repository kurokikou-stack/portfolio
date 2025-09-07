package com.windnauts.gvidas;

//ライブラリのインポート
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Firebase関連
import com.google.firebase.database.FirebaseDatabase;

//UsbSerial通信のドライバー関連
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

//地図の表示関連
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

//setting_page
import android.content.Intent;



//MainActibity: アプリ起動後最初に走るクラス
public class MainActivity extends AppCompatActivity {

    //firebaseのインスタンス化
    private static FirebaseDatabase database = FirebaseDatabase.getInstance("https://gvidas-f481b-default-rtdb.asia-southeast1.firebasedatabase.app/");

    //各種UIの紐づけ
    private TextView checkView; //通信状態のチェック
    private ProgressBar speedBar; //機速の表示バー
    private ProgressBar heightBar; //高度の表示バー

    private ProgressBar ele_up_bar; //eleupのバー
    private ProgressBar ele_down_bar; //eledown表示バー
    private ProgressBar rud_right_bar; //eleupのバー
    private ProgressBar rud_left_bar; //eledown表示バー
    private Button startButton; //表示開始ボタン

    private TextView groundspeedView; //対地速度表示(文字)
    private TextView flightDistanceView; //飛行距離表示(文字)
    private TextView airspeedview; //対気速度表示(文字)
    private TextView heightView; //高度表示(文字)
    private ImageView aircraftIcon; //飛行機アイコン
    private ImageView aircraftIconRoll; //飛行機アイコン(ロール表示)
    private ImageView aircraftIconPitch; //飛行機アイコン(ピッチ表示)

    //各種変数の宣言と初期化
    private int counter = 0; //Start, Stopボタンに用いるカウンター(2の倍数ならカウントアップ、そうでないならストップ)

    private UsbListener usbListener; //スマホのUSB通信
    private SerialInputOutputManager usbIoManager; //スマホのUSB通信
    private ExecutorService executor;  //別のクラスに定義されたコードを走らせたいときに用いるライブラリ。
    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView mapView = null;
    private Context context;
    //プラットフォーム、沖島、竹生島パイロンの位置をddd.dddd形式で指定。毎年コンテスト直前に公開されるので更新すること。
    static final float latstart = (float) 35.294230;
    static final float lonstart = (float) 136.254344;
    static final float latchikubu = (float) 35.416626;
    static final float lonchikubu = (float) 136.124324;
    static final float latoki = (float) 35.250789;
    static final float lonoki = (float) 136.063712;
    static final float lattake = (float) 35.2961122;
    static final float lontake = (float) 136.1784076;



    //>>>>起動時に走るプログラム<<<<
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //Firebaseのデータ一括削除用(ノードgetReference(○○)で指定してremoveValueで全消去)
        //Firebase側からでは一括削除ができないため、念のため残しておきます。
        /*
        DatabaseReference myRef = database.getReference("2023-5-17");
        myRef.removeValue();
        */

        super.onCreate(savedInstanceState);
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);
        context = this;

        //****画面上の表示要素の指定と初期設定****
        //画面上の表示要素を変数と紐づける
        startButton = (Button)findViewById(R.id.button);                    //アプリの動作開始ボタン
        Chronometer FlightTime = (Chronometer)findViewById(R.id.timer);     //動作開始からの経過時間
        checkView = (TextView) findViewById(R.id.check);                    //通信状態のチェック
        airspeedview = (TextView) findViewById(R.id.airspeed);              //対気速度表示(テキスト)
        heightBar = (ProgressBar) findViewById(R.id.height_bar);            //高度表示バー
        heightView = (TextView) findViewById(R.id.height);                  //高度表示(テキスト)
        mapView = (MapView)findViewById(R.id.mapview);                      //地図表示
        flightDistanceView = (TextView) findViewById(R.id.flightdistance);  //飛行距離表示(テキスト)
        aircraftIcon = (ImageView) findViewById(R.id.aircrafticon);         //飛行機アイコン
        aircraftIconRoll = (ImageView) findViewById(R.id.aircraft_roll);    //飛行機アイコン(ロール表示)
        aircraftIconPitch = (ImageView) findViewById(R.id.aircraft_pitch);  //飛行機アイコン(ピッチ表示)
        ele_up_bar = (ProgressBar) findViewById(R.id.ele_up_bar);           //ele_up表示bar
        ele_down_bar = (ProgressBar) findViewById(R.id.ele_down_bar);       //ele_down表示bar
        rud_right_bar = (ProgressBar) findViewById(R.id.rud_right_bar);     //rud_right表示bar
        rud_left_bar = (ProgressBar) findViewById(R.id.rud_left_bar);       //ele_left表示bar



        //緯度経度のスタート地点：リセットボタンを押した時の値
        mapView.getController().setZoom(12.0); //地図のズームをセット
        mapView.getController().setCenter(new GeoPoint(latstart, lonstart)); //地図の中心をセット(プラットフォームに指定)

        //プラットフォーム、竹生島、沖島の座標のピンを立てる
        Marker marker = new Marker( mapView );
        marker.setPosition( new GeoPoint(latstart, lonstart) );
        marker.setTitle ("プラホ");
        mapView.getOverlays().add(marker);
        Marker marker2 = new Marker( mapView );
        marker2.setPosition( new GeoPoint(latchikubu, lonchikubu) );
        marker2.setTitle ("竹生島");
        mapView.getOverlays().add(marker2);
        Marker marker3 = new Marker( mapView );
        marker3.setPosition( new GeoPoint(latoki, lonoki) );
        marker3.setTitle ("沖島");
        mapView.getOverlays().add(marker3);
        Marker marker4 = new Marker( mapView );
        marker4.setPosition( new GeoPoint(lattake, lontake) );
        marker4.setTitle ("多景島");
        mapView.getOverlays().add(marker4);

        //外部ストレージの許可：マップの表示のために必要(??)
        requestPermissionsIfNecessary(new String[] {
                // if you need to show the current location, uncomment the line below
                // Manifest.permission.ACCESS_FINE_LOCATION,
                // WRITE_EXTERNAL_STORAGE is required in order to show the map
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        });


        //****USBシリアル通信の設定****
        //ProbeTableを使って接続設定を行う
        ProbeTable customTable = new ProbeTable();
        //USB-シリアル変換素子のベンダーID(VID)、プロダクトID(PID)をデバイスに応じて変える
        customTable.addProduct(0x2341,0x0043,CdcAcmSerialDriver.class); //Arduino Uno
        customTable.addProduct(0x2341,0x0042,CdcAcmSerialDriver.class); //Arduino Mega
        customTable.addProduct(0x2A50,0x204B,CdcAcmSerialDriver.class); //Arduino Uno 秋月互換品
        customTable.addProduct(0x0403,0x6001,CdcAcmSerialDriver.class); //Arduino Nano:うまくできなかった
        customTable.addProduct(0x10C4, 0xEA60, CdcAcmSerialDriver.class); //ESP32:うまくできなかった
        UsbSerialProber prober = new UsbSerialProber(customTable);

        //接続されたデバイスに使えるデバイスマネージャーを探してくる
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);


        //ドライバーが使えないとき、デバイスが見つかりませんと表示
        if (availableDrivers.isEmpty()) {
            Toast.makeText(MainActivity.this,"デバイスが見つかりません",Toast.LENGTH_LONG).show();
            return;
        }

        //最初の有効なドライバーで接続を試みる
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());

        //接続できなければ、デバイスに接続できませんと表示
        if (connection == null) {
            //add UsbManager.requestPermission(driver.getDevice(), ..) handling here  //??
            Toast.makeText(MainActivity.this, "デバイスに接続できません", Toast.LENGTH_LONG).show();
            return;
        }

        //大体のデバイスはポート0でOK、もしだめなら変えてみる
        UsbSerialPort port = driver.getPorts().get(0);

        //USB接続を試みる。できなければ接続を確立できませんと表示
        try{
            port.open(connection);
            port.setDTR(true); //arduinoのとき
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE); //baudrateは通信速度（場合によって変更）
            //接続状態の表示をいじる
            checkView.setText("Connected");
            checkView.setTextColor(Color.GREEN);
            //表示状態の初期化
            InitUI();
        }
        catch (Exception e3){
            Toast.makeText(MainActivity.this,"接続を確立できません",Toast.LENGTH_LONG).show();
            return;
        }
        String ref = "hoge";


        //****アプリ全体の動作開始・停止の制御****
        //動作の開始、終了ボタンの設定
        startButton.setOnClickListener(new View.OnClickListener() {
            //押されたらスタートする
            @Override
            public void onClick(View view) {
                if(counter % 2 == 0){
                    //ボタンの文字を変える
                    startButton.setText("STOP");
                    //タイマーのリセットと開始
                    FlightTime.setBase(SystemClock.elapsedRealtime());
                    FlightTime.start();
                    //USB通信の開始：Usblistenerの動作を開始し、通信とデータの整形や表示、データベースへの送信を行う。
                    usbIoManager = new SerialInputOutputManager(port, new UsbListener(database,ref,context)); //ここからはUsbListener.javaが動作
                    executor = Executors.newSingleThreadExecutor();
                    executor.submit(usbIoManager);  //UsbListener(外部クラス)を動作させる。
                }
                else{
                    //ボタンの文字を変える
                    startButton.setText("START");
                    //タイマーの終了
                    FlightTime.stop();
                    //USB通信の終了
                    usbIoManager.stop();
                    usbIoManager = null;
                    executor = null;
                    //表示の初期化
                    InitUI();
                }
                counter++;
            }
        });

       //****設定画面の表示****
       Button buttonSettings = findViewById(R.id.settingbutton);
       buttonSettings.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
               startActivity(intent);
           }
       });

    }


    //>>>>アプリがホーム画面から復帰したときの関数：特に動作しない<<<<
    @Override
    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        mapView.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }


    //>>>>アプリがホーム画面に移行したときの関数：特に動作しない<<<<
    @Override
    public void onPause() {
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        mapView.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }


    //>>>>表示の初期化関数<<<<
    public void InitUI(){
        flightDistanceView.setText("---");
        ele_down_bar.setProgress(0);
        ele_up_bar.setProgress(0);
        rud_right_bar.setProgress(0);
        rud_left_bar.setProgress(0);
        heightBar.setProgress(0);
    }


    //>>>>スマホのストレージの許可取得関数<<<<
    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                // permission is not granted
                permissionsToRequest.add(permission);
            }
        }
        if (permissionsToRequest.size() > 0) {
            int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

}