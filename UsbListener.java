package com.windnauts.gvidas;

import static com.windnauts.gvidas.Data.dist_from_pf;
import static com.windnauts.gvidas.Data.echo_alt;
import static com.windnauts.gvidas.Data.pitch;
import static com.windnauts.gvidas.Data.roll;
import static com.windnauts.gvidas.Data.ele;
import static com.windnauts.gvidas.Data.rud;
import static com.windnauts.gvidas.Data.timestamp;
import static com.windnauts.gvidas.MainActivity.latstart;
import static com.windnauts.gvidas.MainActivity.lonstart;
import static com.windnauts.gvidas.MainActivity.latchikubu;
import static com.windnauts.gvidas.MainActivity.lonchikubu;
import static com.windnauts.gvidas.MainActivity.latoki;
import static com.windnauts.gvidas.MainActivity.lonoki;
import static com.windnauts.gvidas.MainActivity.lattake;
import static com.windnauts.gvidas.MainActivity.lontake;
import static java.lang.String.valueOf;

import android.os.Environment;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

//Firebase関連
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

//Arduinoとのシリアル通信関連
import com.hoho.android.usbserial.util.SerialInputOutputManager;

//地図表示関連
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.stream.IntStream;

//UsbListener: SerialInputOutputManagerを継承しているクラス
public class UsbListener implements SerialInputOutputManager.Listener {
    //変数の設定
    private final int DATA_NUM = 15;            //データ長さ：送られてくる構造体内のデータ個数に変更すること
    private byte[] DataBuff = new byte[100];    //送られてくるデータの一時保存場所
    private int gps_interval_counter = 0;       //GPS描画の間隔
    private int index = 0;

    //速度計・高度計の上限値・下限値の設定
    private final double MAXSPEED = 9.0;
    private final double MINSPEED = 0.0;
    private final double MAXHEIGHT = 10;
    private final double MINHEIGHT = 0;
    private final double MAXELE = 10;
    private final double MAXRUD = 10;

    //クラスの読み込みと名前の変更
    private DatabaseReference mref;
    private String ref;
    private List<GeoPoint> geoPoints; //GPSで取得した座標のリスト
    private List<GeoPoint> tmpGeoPoints;
    private Context context; //contextクラス：アプリの実行状態を管理

    //画面上の表示要素の変数
    private TextView checkView;
    private ProgressBar speedBar; //速度バー
    private TextView groundspeedView; //対地速度(テキスト)
    private TextView airspeedView; //対気速度(テキスト)

    //一時的に停止
    private ProgressBar heightBar; //高度バー

    private ProgressBar ele_up_bar; //eleupのバー
    private ProgressBar ele_down_bar; //eledown表示バー
    private ProgressBar rud_right_bar;  //rud右のバー
    private ProgressBar rud_left_bar;   //rud左のバー
    private  TextView eleView;
    private  TextView rudView;
    private TextView heightView; //高度(テキスト)
    private Button resetButton; //リセットボタン
    private  Button settingButton;
    private  int setcounter = 0;
    private TextView flightDistanceView; //飛行距離(テキスト)
    private ImageView aircraftIcon; //飛行機アイコン
    private ImageView aircraftIconRoll; //飛行機アイコン(ロール表示)
    private ImageView aircraftIconPitch; //飛行機アイコン(ピッチ表示)
    private MapView mapView; //地図
    public double rollinit = 0;  //ロールの補正角度(Resetを押したときの値に設定)
    public double pitchinit = 0;  //ピッチの補正角度(Resetを押したときの値に設定)
    public double echo_altinit = 0.0;  //高度の補正値(Resetを押したときの値に設定)
    public double eleplus5 = 600.0;  //eleの補正値(Resetを押したときの値に設定)
    public double eleminus5 = 0.0;
    public double rudplus5 = 600.0;  //rudの補正値(Resetを押したときの値に設定)
    public  double rudminus5 = 0.0;
    private Polyline routeLine; //地図上のルート表示
    private double lat; //緯度経度(floatでは桁不足のためdouble、整数値で受け取ってData.java中で小数値に直している。)
    private double lon;
    private double earthr = 6378.137; //地球の半径(km)、飛行距離の計算に用いる。
    private GeoPoint centerPoint;  //地図の中心座標を入れておく変数
    public String today;  //日時の値


    //>>>>今日の日付の取得をする関数<<<<
    public static String getNowDate(){
        final DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        final Date date = new Date(System.currentTimeMillis());
        return df.format(date);
    }


    //>>>>UsbListenerのメイン関数<<<<
    public UsbListener(FirebaseDatabase database, String ref, Context context){
        //MainActivityから投げられたコンテクストを受け取る
        this.context = context;
        //今日の日付の取得
        Calendar mcl = Calendar.getInstance();
        today = valueOf(mcl.get(Calendar.YEAR)) + "-" + valueOf(mcl.get(Calendar.MONTH)+1) + "-" + valueOf(mcl.get(Calendar.DATE));
        //MainActivityから投げられたデータベースの値を受け取る。
        this.mref = database.getReference();
        //ルート、座標の初期化
        this.geoPoints = new ArrayList<GeoPoint>();
        this.tmpGeoPoints = new ArrayList<GeoPoint>();
        this.routeLine = new org.osmdroid.views.overlay.Polyline();
        this.ref = ref;

        getViews(); //getViewsを呼び出す
    }


    //>>>>画面上の表示要素を取得し、名前付けする関数<<<<
    private void getViews(){
        flightDistanceView = (TextView)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.flightdistance);
        heightBar = (ProgressBar)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.height_bar);
        heightView = (TextView)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.height);
        airspeedView = (TextView) ((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.airspeed);
        mapView = (MapView)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.mapview);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        checkView = (TextView)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.check);
        resetButton = (Button)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.resetbutton);
        settingButton = (Button)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.settingbutton);
        aircraftIcon = (ImageView)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.aircrafticon);
        aircraftIconPitch = (ImageView)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.aircraft_pitch);
        aircraftIconRoll = (ImageView)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.aircraft_roll);

        eleView = (TextView)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.ele);
        ele_up_bar = (ProgressBar)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.ele_up_bar);
        ele_down_bar = (ProgressBar)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.ele_down_bar);

        rudView = (TextView)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.rud);
        rud_right_bar = (ProgressBar)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.rud_right_bar);
        rud_left_bar = (ProgressBar)((com.windnauts.gvidas.MainActivity)context).findViewById(R.id.rud_left_bar);

        //リセットボタンを押したら, 出発地点をその位置で変更する
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //ロール、ピッチ、高度をリセットボタンを押したときの値で0合わせ。
                rollinit = roll;
                pitchinit = pitch;
                echo_altinit = echo_alt;
                //ルートの削除
                geoPoints.clear();
                mapView.getOverlays().clear();
                //リセットボタンで消えるため、プラットフォーム、竹生島、沖島の座標のピンを立てる
                setMarker();
                Toast.makeText(context, "Reset RouteLine & Offset Posture/Altitude", Toast.LENGTH_LONG).show();
            }
        });

    }


    //>>>>新しいデータが送られたときにCOBSのデコードとデータの表示、記録をする関数<<<<//
    @Override
    public void onNewData(final byte[] data) {
        int dataLen = data.length;
        int counter = 0; //0の個数のカウンタ

        //0(0x00)のデータが1つ現れるまで、counterで0の個数を数えながら繰り返し受信
        for(int i=0; i<dataLen; i++) {
            DataBuff[index + i] = data[i];

            if (data[i] == 0x00) {
                counter++;
            }

            if (counter == 1) {
                break;
            }
        }


        //****送られてきたデータのデコードと画面への表示、サーバー送信****
        if(counter == 1){
            counter = 0; //0の個数のカウンターをここで0に戻す。
            index = 0;
            final float[] Data = decode(DataBuff);  //受信したデータのデコード
            Data mData = new Data(Data); //Data.javaをインスタンス化し、値を格納。

            timestamp = getNowDate();  //受信時刻のタイムスタンプ

            //データの表示用のデータ取得・加工
            //mData.get○○○()でData.javaに格納されている値をRead onlyで読み取り
            //roll, pitch, echo_altはData.java上のデータを直接いじっている。
            roll = Math.floor((roll-rollinit)*10)/10;
            pitch = Math.floor((pitch-pitchinit)*10)/10;
            echo_alt = Math.floor(echo_alt - echo_altinit);
            ele = Math.floor(10.0 * mData.getEle() / (eleplus5 - eleminus5) - 5.0);
            rud = Math.floor(10.0 * mData.getRud() / (rudplus5 - rudminus5) - 5.0);
            double height = echo_alt / 100;
            lat = mData.getLat();
            lon = mData.getLon();
            double airspeed = mData.getAirspeed();
            double groundspeed = mData.getGroundspeed();
            int speedTmp = (int) ((groundspeed - MINSPEED) / (MAXSPEED - MINSPEED) * 100);
            int heightTmp = (int) ((height - MINHEIGHT) / (MAXHEIGHT - MINHEIGHT) * 100);
            int eleTmp = (int) (ele / MAXELE * 100.0);
            int rudTmp = (int) (rud / MAXRUD * 100.0);

            final int relativeHeightValue;
            final int relativeEleValue;
            final int relativeRudValue;


            //緯度経度をddd.dddd形式（小数で表した度数）にして、プラットフォームからの距離を計算する
            //計算式の参考( https://keisan.casio.jp/exec/system/1257670779 )
            double dist = (earthr * Math.acos((Math.cos(lat/180*Math.PI))*(Math.cos(latstart/180*Math.PI))*(Math.cos((lon-lonstart)/180*Math.PI))+((Math.sin(lat/180*Math.PI))*(Math.sin(latstart/180*Math.PI)))));
            if(Double.isNaN(dist) == false) { //値がNaNになるエラーが起きることがあるので、それを排除。
                dist_from_pf = Math.floor(dist*100)/100;
            }

            //一定間隔で飛行ルートを描画。10回データを受信するごとに1回描画。
            if(gps_interval_counter >= 10){
                //画面上で表示している座標数が100以上になったら、偶数番目に格納されている座標データを残し、奇数番目を削除
                //→おおよそ100個程度座標を表示すると処理が重くなるため、過去の飛行ルートはどんどん粗く表示する。
                if(geoPoints.size() > 100){
                    for(int i=0; i<geoPoints.size(); i++){
                        tmpGeoPoints.add(geoPoints.get(i));
                    }
                    geoPoints.clear();
                    for(int i=0; i<tmpGeoPoints.size()/2; i++){
                        geoPoints.add(tmpGeoPoints.get(i*2));
                    }
                    tmpGeoPoints.clear();
                }

                //リストに現在位置の代入
                geoPoints.add(new GeoPoint(lat,lon));
                routeLine.setPoints(geoPoints);
                mapView.getOverlayManager().add(routeLine);
                gps_interval_counter = 0;
            }
            else {
                gps_interval_counter++;
            }

            //バーの表示範囲を超えたときの設定 minを100に設定
            relativeHeightValue = Math.min(heightTmp, 100);
            if(ele>0){
                relativeEleValue = Math.min(eleTmp, 100);
            }else{
                relativeEleValue = Math.min(-eleTmp, 100);
            }

            if(rud>0){
                relativeRudValue = Math.min(rudTmp, 100);
            }else{
                relativeRudValue = Math.min(-eleTmp, 100);
            }


            // ****Firebase(Realtime Database)へのデータ送信****
            Map<String ,Object> firebasedata = mData.toMap(); //jsonに対応した形式に整形
            //リアルタイム閲覧のためのデータをFirebaseに送信
            mref.child("000_RTview").setValue(firebasedata);
            //Unix形式の現在時刻をキーとしたログ目的のデータをFirebaseに送信
            String key = valueOf(System.currentTimeMillis());
            mref.child("002_Log").child(today).child(key).setValue(firebasedata);

            //****スマホ内へのログファイルの出力****
            try {
                //出力先を作成（/Android/data/com.windnauts.gvidas/Documents内に保存）
                FileWriter fw = new FileWriter(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) + "/log_" + today + ".csv", true);
                PrintWriter pw = new PrintWriter(new BufferedWriter(fw));

                pw.print(timestamp); //Edited 6/27
                pw.print(",");
                pw.print(height);
                pw.print(",");
                pw.print(mData.getGps_alt());
                pw.print(",");
                pw.print(airspeed);
                pw.print(",");
                pw.print(groundspeed);
                pw.print(",");
                pw.print(lat);
                pw.print(",");
                pw.print(lon);
                pw.print(",");
                pw.print(mData.getHeading());
                pw.print(",");
                pw.print(mData.getRoll());
                pw.print(",");
                pw.print(mData.getPitch());
                pw.print(",");
                pw.print(mData.getTemp());
                pw.print(",");
                pw.print(mData.getHumid());
                pw.print(",");
                pw.print(mData.getAtm());
                pw.print(",");
                pw.print(dist_from_pf);
                pw.print(",");
                pw.print(mData.getRot());
                pw.println();
                pw.close();
            } catch (IOException ex) {
                //例外時処理
                Log.d("fileio","err");
                ex.printStackTrace();
            }

            settingButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public  void  onClick(View View) {
                    if(setcounter % 5 ==0){
                        settingButton.setText("ELE+5");
                        setcounter+=1;
                    }
                    else if(setcounter % 5 == 1){
                        settingButton.setText("ELE-5");
                        eleplus5 = mData.getEle();
                        setcounter+=1;
                    }
                    else if(setcounter % 5 == 2){
                        settingButton.setText("RUD+5");
                        eleminus5 = mData.getEle();
                        setcounter+=1;
                    }
                    else if(setcounter % 5 == 3){
                        settingButton.setText("RUD-5");
                        rudplus5 = mData.getRud();
                        setcounter+=1;
                    }
                    else if(setcounter % 5 == 4){
                        settingButton.setText("SET");
                        rudminus5 = mData.getRud();
                        setcounter+=1;
                    }
                }
            });

            //****アプリの画面表示の操作****
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    //UIスレッドに干渉するのでHandlerを使って画面上の要素を操作する
                    airspeedView.setText(valueOf(airspeed));                    //対気速度
                    flightDistanceView.setText(valueOf(dist_from_pf));          //累積飛行距離

                    heightBar.setProgress(relativeHeightValue);
                    if(ele>0){
                        ele_up_bar.setProgress(relativeEleValue);
                        ele_down_bar.setProgress(0);
                    }else{
                        ele_up_bar.setProgress(0);
                        ele_down_bar.setProgress(relativeEleValue);
                    }

                    if(rud>0){
                        rud_right_bar.setProgress(relativeRudValue);
                        rud_left_bar.setProgress(0);
                    }else{
                        rud_left_bar.setProgress(0);
                        rud_right_bar.setProgress(relativeRudValue);
                    }

                    eleView.setText(valueOf(ele));
                    rudView.setText(valueOf(rud));

                    heightView.setText(valueOf(echo_alt));                      //高度
                    aircraftIcon.setRotation((int) mData.getHeading());         //方位
                    aircraftIconRoll.setRotation((float)(mData.getRoll())*2);   //ロール
                    aircraftIconPitch.setRotation((float)-(mData.getPitch())*2);//ピッチ
                    IMapController mapController = mapView.getController();
                    centerPoint = new GeoPoint(lat,lon);                        //地図の中心点を作成
                    mapController.setCenter(centerPoint);                       //指定した位置を地図の中心に表示する
                }
            });
        }
        else {
            index += dataLen;
        }
    }



    //>>>>エラー時に再起動インテントを投げる関数<<<<
    @Override
    public void onRunError(Exception e) {
        //restartApp();
        Log.e("RunException","onRunError is called");
        Toast.makeText(context,"エラーが発生しました。強制的に再起動します。",Toast.LENGTH_LONG).show();
        restartApp();
    }



    //>>>>COBSのデコードとbyteからfloatへの変換関数<<<<
    private float[] decode(byte[] data){
        boolean first = true;
        int zeroindex = 0;
        byte[] result = new byte[4*DATA_NUM+2]; //COBSではFloatの4byte*データ個数+2個が送られる
        float[] ret = new float[DATA_NUM]; //デコード結果を返す変数

        //COBSの復号をする
        for(int i=0;i<DATA_NUM*4+2;i++) {
            byte b = data[i];
            //最初だけ読み飛ばす
            if(first){
                zeroindex = b;
                first = false;
            }
            else{
                if (i == zeroindex) { //本来0の入った位置のとき
                    result[i] = 0; //0を結果に返す
                    zeroindex = i + Byte.toUnsignedInt(b); //次の0位置(インデックス)の計算
                }
                else{ //そうでないとき
                    result[i] = data[i]; //そのまま結果に入れる
                }
            }
        }

        //複合したデータをFloatにして返す
        for(int i=0;i<DATA_NUM;i++){
            byte[] tmpresult = new byte[4];
            for(int j=0;j<4;j++){ //最初1つを読み飛ばして、Float4byteづつ読んでいく
                tmpresult[j] = result[4*i+j+1];
            }
            ret[i] = byteArrayToFloat(tmpresult); //返り値retにFloatに変換して入れる
        }

        return ret;
    }


    //>>>>byteからFloatへ変換する関数<<<<
    public static float byteArrayToFloat(byte[] bytes) {
        int intBits = bytes[3] << 24
                | (bytes[2] & 0xFF) << 16
                | (bytes[1] & 0xFF) << 8
                | (bytes[0] & 0xFF);
        return Float.intBitsToFloat(intBits);
    }

    public void setMarker(){
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
    }

    //>>>>エラー時にアプリの再起動をする関数<<<<
    private void restartApp() {
        Context context = this.context.getApplicationContext();
        //Intent intent = RestartActivity.createIntent(context,ref);
        // RestartActivity を起動（AndroidManifest.xml での宣言により別プロセスで起動する
        //context.startActivity(intent);
    }
}