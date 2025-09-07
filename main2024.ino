#include <stdlib.h>
#include <Wire.h>
#include "SparkFun_u-blox_GNSS_v3.h" //Click here to get the library: http://librarymanager/All#SparkFun_u-blox_GNSS_v3
#include <SPI.h>
#include <math.h>
#include"BME280.h"
#include <MadgwickAHRS.h>
Madgwick MadgwickFilter;
#include <stdio.h>
#include <string.h>


SFE_UBLOX_GNSS myGNSS;  //GPSのライブラリを呼び出し
BME280_F bme280_f = BME280_F(); //環境センサのライブラリを呼び出し

#define DATA_NUM 15         //送信データの個数、4byte*個数で定義
#define TINY_NUM 3

#define sensorPin A7        //高度計(アナログ)のピン指
float env_data[3];          //気温気圧の一時保管用変数

#define Addr_Accl 0x19  // (JP1,JP2,JP3 = Open)
#define Addr_Gyro 0x69  // (JP1,JP2,JP3 = Open)

/*
加速度センサ用変数設定
*/
#define Addr_Mag 0x13   // (JP1,JP2,JP3 = Open)
float xAccl = 0.00;
float yAccl = 0.00;
float zAccl = 0.00;
float xGyro = 0.00;
float yGyro = 0.00;
float zGyro = 0.00;
float   xMag  = 0;
float   yMag  = 0;
float   zMag  = 0;

//データ構造（構造体）の定義
union SENSORDATA
{
  struct{
    float rot;            //data[0], ロータリーエンコーダ回転数, ok
    float echo_alt;       //data[1], 超音波センサ高度, ok
    float gps_alt;        //data[2], GPS高度, ok
    float airspeed;       //data[3], 対気速度, ok
    float groundspeed;    //data[4], 対地速度, ok
    float latitude;       //data[5], 緯度のddddddddd, ok
    float longtitude;     //data[6], 経度のdddddddddd, ok
    float heading;        //data[7], 方位, ok
    float roll;           //data[8], ロール, ok
    float pitch;          //data[9], ピッチ, ok
    float temp;           //data[10], 機内温度, ok
    float humid;          //data[11], 機内湿度, ok
    float atm;            //data[12], 機内気圧, ok
    float ele;            //data[13], エレベーター, ok
    float rud;            //data[14], ラダー, ok
  };
  byte bin[sizeof(float)*DATA_NUM];
};

SENSORDATA data; //共用体をdataという名前で使う
byte message[(sizeof(float))*(2+DATA_NUM)]; //送信データの長さを定義

//tinyからの通信をcobsで行うための準備　本番では断念
// データ構造（構造体）の定義
union TINYDATA {
  struct {
    int echo_alt; // 超音波センサ高度
    int ele;      // エレベーター角度
    int rud;      // ラダー角度
  };
  byte bin[sizeof(int) * TINY_NUM];
};
TINYDATA decoded_data;
//舵角tinyから取得するデータを格納する配列
byte tiny_message[(sizeof(int))*(2 + TINY_NUM)];
// デコードしたデータを保持するための配列
byte decoded_message[sizeof(int) * TINY_NUM];

unsigned long start_ = millis();
unsigned long gnssstart_ = millis();

//setup
void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600); //Serial: USB端子/Xbeeを通じた通信
  while (!Serial); //シリアルが使えるようになるまで待つ。
  Serial1.begin(9600, SERIAL_8N1, 0, 4); //Serial for ATtiny
  while(!Serial1);
  Serial2.begin(9600, SERIAL_8N1, 16, 17); //Serial for ATtiny
  while(!Serial2);
  Wire.begin();
  MadgwickFilter.begin(10); //9軸センサ補正フィルター
  bme280_f.setup_temp_humid_atm(); //温度、湿度、気圧センサのセットアップ
  gnss_setup(); //gpsのセットアップ
  // set_debug();
}



//loop
void loop() {
    
  // put your main code here, to run repeatedly:

  //データの取得
  get_gnss();       //GPS
  get_airspeed();   //ロータリーエンコーダ
  get_env();        //温度湿度気圧
  delay(1);
  get_tiny();        //舵角+高度
  delay(1);
  get_accl();         //姿勢角

  
  if(millis()-start_ >= 500){  //前回の送信から500msを超えるごとに実行
    start_ = millis();
    //cobsの形式でエンコード(データの区切りがあることでスマホでの受信動作が安定)
    data_to_cobs();
    //データ送信
    Serial.write(message,sizeof(SENSORDATA)+2);
    // //Serial.println(start_); //デバッグ用
  }
  
  //デバッグ用(Serial.writeは01のバイナリデータのため、Teratermなどでしか読めない。)
  
  // Serial.print("lat");
  // Serial.print(data.latitude);
  // Serial.print(", long");
  // Serial.print(data.longtitude);
  // Serial.print(", alt");
  // Serial.print(data.gps_alt);
  // Serial.print(", head");
  // Serial.print(data.heading);
  // Serial.print(", gs");
  // Serial.print(data.groundspeed);
  // Serial.print(", roll");
  // Serial.print(data.roll);
  // Serial.print(", pitch");
  // Serial.print(data.pitch);
  // Serial.print(", temp");
  // Serial.print(data.temp);
  // Serial.print(", humid");
  // Serial.print(data.humid);
  // Serial.print(", atm");
  // Serial.print(data.atm);
  // Serial.print(", airspeed_rot");
  // Serial.print(data.airspeed);
  // Serial.print(", echo_alt");
  // Serial.print(data.echo_alt);
  // Serial.print(", ele");
  // Serial.print(data.ele);
  // Serial.print(", rud");
  // Serial.println(data.rud);
  
  
  delay(10);
}


//ここから先はメインの関数内で呼び出す関数//


//GPSモジュールセットアップ
void gnss_setup(){
  do{
    if (myGNSS.begin(Wire, 0x42) == false){
    // Serial.println(F("u-blox GNSS module not detected at default I2C address. Please check wiring. Freezing."));
    myGNSS.factoryReset(); //リセット
    delay(100);
    }
  }while (0);
   
  myGNSS.setNavigationFrequency(10);    //測位レートの設定(10times/s)
  myGNSS.setI2COutput(COM_TYPE_UBX);    //Set the I2C port to output UBX only (turn off NMEA noise)
  myGNSS.saveConfiguration();           //Save the current settings to flash and BBR
}


//GPSモジュール
//桁数をスマホの処理と合わせることに注意！
void get_gnss(){
    //緯度経度; Arduinoのfloat,doubleは小数点以下の桁数が不十分なため、整数値としてスマホへ送信
    data.latitude = myGNSS.getLatitude(); // (10^-7 degrees)
    data.longtitude = myGNSS.getLongitude(); // (10^-7 degrees)

    //機首方向; GPSモジュールに方向性があるため設計時注意
    data.heading = myGNSS.getHeading(); // (*10^-5 degrees)
    
    //対地速度
    data.groundspeed = myGNSS.getGroundSpeed(); // (mm/s)
    
    //GPS高度（精度0.5mなので注意）
    data.gps_alt = myGNSS.getAltitudeMSL(); //海面高度(Mean Sea Level) (mm)
}


//環境データ（温度湿度気圧）
void get_env(){
  /*
  Config
  Arduino 5V - BME280 Vcore, CSB (Pin4, 6)
  Arduino GND - BME280 GND, SDO (Pin5, 1)
  Arduino SDA - BME280 SDI (Pin3)
  Arduino SCL - BME280 SCK (Pin2)
  */
  bme280_f.get_temp_humid_atm(env_data); //温度湿度気圧
  data.temp = env_data[0];
  data.humid = env_data[1];
  data.atm = env_data[2];
}

//風速計
void get_airspeed(){
  Serial1.write("a");
  if(Serial1.available()){
    String num = Serial1.readStringUntil('\n');
    data.airspeed = 0.1164*num.toFloat() + 0.4122; //シリアルで受け取った値は文字列のため、数値へ変換
  }
}


//高度計
void get_alt(){
  /*
  Config
  Arduino 5V - MB1260 V+(Pin6)
  Arduino GND - MB1260 GNC(Pin7)
  Arduino A7 - MB1260 PW(Pin3)
  */
  float val = analogRead(sensorPin)*1;
  data.echo_alt = val*2; //単位はcm
}

//=====================================================================================//
//9軸センサ
void BMX055_Init()
{
  //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Accl);
  Wire.write(0x0F); // Select PMU_Range register
  Wire.write(0x03);   // Range = +/- 2g
  Wire.endTransmission();
  delay(100);
 //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Accl);
  Wire.write(0x10);  // Select PMU_BW register
  Wire.write(0x08);  // Bandwidth = 7.81 Hz
  Wire.endTransmission();
  delay(100);
  //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Accl);
  Wire.write(0x11);  // Select PMU_LPW register
  Wire.write(0x00);  // Normal mode, Sleep duration = 0.5ms
  Wire.endTransmission();
  delay(100);
 //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Gyro);
  Wire.write(0x0F);  // Select Range register
  Wire.write(0x04);  // Full scale = +/- 125 degree/s
  Wire.endTransmission();
  delay(100);
 //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Gyro);
  Wire.write(0x10);  // Select Bandwidth register
  Wire.write(0x07);  // ODR = 100 Hz
  Wire.endTransmission();
  delay(100);
 //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Gyro);
  Wire.write(0x11);  // Select LPM1 register
  Wire.write(0x00);  // Normal mode, Sleep duration = 2ms
  Wire.endTransmission();
  delay(100);
 //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Mag);
  Wire.write(0x4B);  // Select Mag register
  Wire.write(0x83);  // Soft reset
  Wire.endTransmission();
  delay(100);
  //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Mag);
  Wire.write(0x4B);  // Select Mag register
  Wire.write(0x01);  // Soft reset
  Wire.endTransmission();
  delay(100);
  //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Mag);
  Wire.write(0x4C);  // Select Mag register
  Wire.write(0x00);  // Normal Mode, ODR = 10 Hz
  Wire.endTransmission();
 //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Mag);
  Wire.write(0x4E);  // Select Mag register
  Wire.write(0x84);  // X, Y, Z-Axis enabled
  Wire.endTransmission();
 //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Mag);
  Wire.write(0x51);  // Select Mag register
  Wire.write(0x04);  // No. of Repetitions for X-Y Axis = 9
  Wire.endTransmission();
 //------------------------------------------------------------//
  Wire.beginTransmission(Addr_Mag);
  Wire.write(0x52);  // Select Mag register
  Wire.write(0x16);  // No. of Repetitions for Z-Axis = 15
  Wire.endTransmission();
}
//=====================================================================================//
void BMX055_Accl()
{
  int data[6];
  for (int i = 0; i < 6; i++)
  {
    Wire.beginTransmission(Addr_Accl);
    Wire.write((2 + i));// Select data register
    Wire.endTransmission();
    Wire.requestFrom(Addr_Accl, 1);// Request 1 byte of data
    // Read 6 bytes of data
    // xAccl lsb, xAccl msb, yAccl lsb, yAccl msb, zAccl lsb, zAccl msb
    if (Wire.available() == 1)
      data[i] = Wire.read();
  }
  // Convert the data to 12-bits
  xAccl = ((data[1] * 256) + (data[0] & 0xF0)) / 16;
  if (xAccl > 2047)  xAccl -= 4096;
  yAccl = ((data[3] * 256) + (data[2] & 0xF0)) / 16;
  if (yAccl > 2047)  yAccl -= 4096;
  zAccl = ((data[5] * 256) + (data[4] & 0xF0)) / 16;
  if (zAccl > 2047)  zAccl -= 4096;
  xAccl = xAccl * 0.0098; // renge +-2g
  yAccl = yAccl * 0.0098; // renge +-2g
  zAccl = zAccl * 0.0098; // renge +-2g
}
//=====================================================================================//
void BMX055_Gyro()
{
  int data[6];
  for (int i = 0; i < 6; i++)
  {
    Wire.beginTransmission(Addr_Gyro);
    Wire.write((2 + i));    // Select data register
    Wire.endTransmission();
    Wire.requestFrom(Addr_Gyro, 1);    // Request 1 byte of data
    // Read 6 bytes of data
    // xGyro lsb, xGyro msb, yGyro lsb, yGyro msb, zGyro lsb, zGyro msb
    if (Wire.available() == 1)
      data[i] = Wire.read();
  }
  // Convert the data
  xGyro = (data[1] * 256) + data[0];
  if (xGyro > 32767)  xGyro -= 65536;
  yGyro = (data[3] * 256) + data[2];
  if (yGyro > 32767)  yGyro -= 65536;
  zGyro = (data[5] * 256) + data[4];
  if (zGyro > 32767)  zGyro -= 65536;

  xGyro = xGyro * 0.0038; //  Full scale = +/- 125 degree/s
  yGyro = yGyro * 0.0038; //  Full scale = +/- 125 degree/s
  zGyro = zGyro * 0.0038; //  Full scale = +/- 125 degree/s
}

//=====================================================================================//
void BMX055_Mag()
{
  int data[8];
  for (int i = 0; i < 8; i++)
  {
    Wire.beginTransmission(Addr_Mag);
    Wire.write((0x42 + i));    // Select data register
    Wire.endTransmission();
    Wire.requestFrom(Addr_Mag, 1);    // Request 1 byte of data
    // Read 6 bytes of data
    // xMag lsb, xMag msb, yMag lsb, yMag msb, zMag lsb, zMag msb
    if (Wire.available() == 1)
      data[i] = Wire.read();
  }
  // Convert the data
  xMag = ((data[1] <<8) | (data[0]>>3));
  if (xMag > 4095)  xMag -= 8192;
  yMag = ((data[3] <<8) | (data[2]>>3));
  if (yMag > 4095)  yMag -= 8192;
  zMag = ((data[5] <<8) | (data[4]>>3));
  if (zMag > 16383)  zMag -= 32768;
}

//BMX055から機体状態取得
//9軸センサのメイン関数
void get_accl(){
  BMX055_Gyro();
  BMX055_Accl();
  MadgwickFilter.updateIMU(xGyro,yGyro,zGyro,xAccl,yAccl,zAccl);
  data.pitch  = MadgwickFilter.getRoll();
  data.roll = MadgwickFilter.getPitch();
}

//舵角tinyからデータを取得
//tinyから送られるデータは 000.000.000で'.'によって区切られている
void get_tiny(){
  Serial2.write("a");
  if (Serial2.available()) {
    String num = Serial2.readStringUntil('\n');
    char separator = '.'; // 分割する文字
    int count=0;
    int index = 0;
    while (index != -1) {
      index = num.indexOf(separator);
      String firstPart = num.substring(0, index);
      if(count ==0){
        data.echo_alt = firstPart.toFloat();
      }
      if(count ==1){
        data.ele = firstPart.toFloat();
      }
      if(count ==2){
        data.rud = firstPart.toFloat();
        count = 0;
      }
      num = num.substring(index + 1);
      count++;
    }
  }
  while(Serial2.available()){
    char t = Serial2.read();
  }
}

//送信データのエンコード
void data_to_cobs(){
  //cobsを使用してパケットの区切りを明確にする
  //cobsについては：https://en.wikipedia.org/wiki/Consistent_Overhead_Byte_Stuffin
  byte data_size = sizeof(SENSORDATA);
  byte zero_place = data_size+1;
  message[data_size+1] = 0x00;
  for(byte i=1;i<=data_size;i++){
    if(data.bin[data_size-i] == 0x00){
      message[data_size-i+1] = zero_place-(data_size-i+1);
      zero_place = data_size-i+1;
    }else{
      message[data_size-i+1] = data.bin[data_size-i];
    }
  }
  message[0] = zero_place;
}

void set_debug(){
  data.rot = 1;             //data[0], ロータリーエンコーダ回転数, ok
  data.echo_alt=200;        //data[1], 超音波センサ高度, ok
  data.gps_alt =300 ;       //data[2], GPS高度, ok
  data.airspeed =10.3;      //data[3], 対気速度, ok
  data.groundspeed= 9.5;    //data[4], 対地速度, ok
  data.latitude=0;          //data[5], 緯度のddddddddd, ok
  data.longtitude=0;        //data[6], 経度のdddddddddd, ok
  data.heading = 100;       //data[7], 方位, ok
  data.roll =20;            //data[8], ロール, ok
  data.pitch =30;           //data[9], ピッチ, ok
  data.temp = 27.4;         //data[10], 機内温度, ok
  data.humid = 89.2;        //data[11], 機内湿度, ok
  data.atm = 965;           //data[12], 機内気圧, ok
  data.ele =200;            //data[13], エレベーター, ok
  data.rud = 300;           //data[14], ラダー, ok
}