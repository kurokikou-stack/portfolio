#ifndef BME280_h
#define BME280_h
#include "Arduino.h"

class BME280_F{
  public:
    BME280_F();
    void setup_temp_humid_atm();
    void get_temp_humid_atm(float env_data[3]);
  private:
    int32_t _BME280_compensate_T_int32(int32_t adc_T); //温度補正関数
    uint32_t _BME280_compensate_H_int32(int32_t adc_H); // 湿度補正関数
    uint32_t _BME280_compensate_P_int32(int32_t adc_P); // 気圧補正関数
};
#endif
