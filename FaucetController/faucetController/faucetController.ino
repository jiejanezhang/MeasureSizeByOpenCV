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
//定义继电器控制管脚
#define relay_pin   25
#define BUF_SIZE    256
char buf[BUF_SIZE];
char info_file[] = "/containers.txt";

void readFile(const char file_name[]){
  if (!SPIFFS.exists(file_name)) Serial.println("File does not exist.");
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
  file.close();
}

void writeFile(char file_name[], char data[] ) {
  if (SPIFFS.exists(file_name)) {
    SPIFFS.remove(file_name);
    Serial.println("Remove existing file first.");
  }
  delay(20);

  File file = SPIFFS.open(file_name, FILE_WRITE);
  if (file)
  {
    file.write((uint8_t *)data, strlen(data));
    Serial.print("Data to store:");
    Serial.println(data);
  }
  file.close();
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
void sendFile(char file_name[]) {
  
  File file = SPIFFS.open(file_name, FILE_READ);
  if (file)
  {
    //u8 line_num = 1;
    while (file.available())
    {
      //Serial.println(line_num++);
      char ch = (char)file.read();
      Serial.print(ch);
      SerialBT.write(ch);
    }
  }
  file.close();
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

  pinMode(relay_pin, OUTPUT);//设置引脚为输出模式
  readFile(info_file);
}

void loop() {

  if (SerialBT.available()) {
    // Serial.write(SerialBT.read());
    bool is_command = false;
    bool to_store = false;
    u8 idx = 0;
    for(u8 i=0;i<255;i++)
    {
      char rc = SerialBT.read();
      if (to_store) {
        if (rc == '&') {
          writeFile(info_file, buf);
          Serial.println("File is received and stored.");
          readFile(info_file);
          to_store = false;
          memset(buf, 0, sizeof(buf));
          break;
        } else {
          buf[idx++] = rc;
          continue;
        }
      }
      if (rc == '#') {
        is_command = true;
        continue;
      }
      if ( is_command ) {
        if (rc == 'G') {
          is_command = false;
          Serial.println("#G command is received. To send file...");
          sendFile(info_file);
          SerialBT.write('&');
          Serial.println("File is sent.");
          break;
        }
        if (rc == 'W') {
          is_command = false;
          to_store = true;
          Serial.println("#W command is received. To receive and store the file...");
          continue;
        }
      }
    }
    //writeFile(info_file, buf);
    //readFile(info_file);
  }
  delay(20);
}


