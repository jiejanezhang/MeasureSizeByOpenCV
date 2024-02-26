#include "public.h"
#include "BluetoothSerial.h"
#include "FS.h"
#include "SPIFFS.h"
#include "string.h"

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

#if !defined(CONFIG_BT_SPP_ENABLED)
#error Serial Bluetooth not available or not enabled. It is only available for the ESP32 chip.
#endif

BluetoothSerial SerialBT;

char buf_name[20];
char buf_vol[10];
char buf[50];
char info_file[] = "/containers.txt";

void readFile(const char file_name[]){
  File file = SPIFFS.open(file_name, FILE_READ);
  if (file)
  {
    //u8 line_num = 1;
    while (file.available())
    {
      //Serial.println(line_num++);
      Serial.print((char)file.read());
    }
  }
}

void writeFile(char file_name[], char data[] ) {
  File file = SPIFFS.open(file_name, FILE_WRITE);
  if (file)
  {
    file.write((uint8_t *)data, strlen(data));
    file.close();
  }
}
void appendFile(char file_name[], char data[])
{
  File fileToWrite = SPIFFS.open(file_name, FILE_APPEND);
  
  if(!fileToWrite){
      Serial.println("There was an error opening the file for writing");
      return;
  }
  
  if(fileToWrite.println(data)){
      Serial.println("File was written");;
  } else {
      Serial.println("File write failed");
  }
  
  fileToWrite.close();
}

void setup() {
  Serial.begin(115200);
  delay(500);
  SerialBT.begin("Faucet_Controller"); //Bluetooth device name
  Serial.println("The device started, now you can pair it with bluetooth!");
  //挂载文件系统
  if (SPIFFS.begin(true))
  {
    Serial.println("SPIFFS文件系统挂载成功！");
  }
}

void loop() {

  if (SerialBT.available()) {
    // Serial.write(SerialBT.read());
    Serial.println("收到数据");
    bool is_name = true;
    u8 vol_ind = 0;
    for(u8 i=0;i<50;i++)
    {
      char rc = SerialBT.read();
      u8 name_len = 0;
      if (rc == '(') {
        is_name = false;
        buf_name[i] = '\0'; // 字符串串尾符
        Serial.print("容器名：");
        Serial.println(buf_name);
        name_len = i;
      }
      else if (rc == 'L') {
        buf_vol[vol_ind] = '\0';
        Serial.print("容量：");
        Serial.println(buf_vol);
      }
      else if ( is_name )
          buf_name[i] = rc;
      else 
      {
          buf_vol[vol_ind++] = rc;
      }
      float volumn = atof(buf_vol);
      buf[i] = rc;
      if (rc == ')') break;
    }
    writeFile(info_file, buf);
    readFile(info_file);
  }
  delay(20);
}


