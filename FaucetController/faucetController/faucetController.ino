#include "public.h"
#include "BluetoothSerial.h"
#include "FS.h"
#include "SPIFFS.h"
#include "string.h"
#include <Keypad.h>

#include <U8g2lib.h>

#ifdef U8X8_HAVE_HW_SPI
#include <SPI.h>
#endif
#ifdef U8X8_HAVE_HW_I2C
#include <Wire.h>
#endif

U8G2_SSD1309_128X64_NONAME0_F_4W_SW_SPI u8g2(U8G2_R0, /* clock=*/ 18, /* data=*/ 23, /* cs=*/ 21, /* dc=*/ 19, /* reset=*/ 2);  



#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

#if !defined(CONFIG_BT_SPP_ENABLED)
#error Serial Bluetooth not available or not enabled. It is only available for the ESP32 chip.
#endif


#define BUF_SIZE    128

#define REL_PIN 2 //继电器控制

BluetoothSerial SerialBT;
const byte ROWS =4;    
const byte COLS =4;
char keys[ROWS][COLS] ={
  {'1','2','3','A'},
  {'4','5','6','B'},
  {'7','8','9','C'},
  {'*','0','#','D'}
  };
byte rowPins[ROWS] = {4, 16,17,5};    //行的接口引脚
byte colPins[COLS] = {14,27,26,25};  //列的接口引脚
Keypad keypad = Keypad( makeKeymap(keys), rowPins, colPins, ROWS, COLS );
unsigned long interval = 0;
unsigned long triggerMillis =0;
// Variables will change:
volatile bool wateringSta = false;  // volatile is asking compiler not to optimize it.

#define MAX_CONTAINER 4
String buf_container[MAX_CONTAINER];
u8 container_num = 0;
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

void loadContainers(const char file_name[]){
  if (!SPIFFS.exists(file_name)) Serial.println("File does not exist.");
  File file = SPIFFS.open(file_name, FILE_READ);
  
  Serial.println("loadContainers...");
  // Clear buf_container array

  if (file)
  {
    u8 i = 0;
    while (file.available())
    {
      String line = file.readStringUntil('\n');
      buf_container[i++] = line;
      Serial.print(line);
    }
    container_num = i;
    for (u8 j=i; j < MAX_CONTAINER ; j++){
      buf_container[j++] = "";
    }
  }
  file.close();
}

void showContainerMenu(const char file_name[]){

  loadContainers(file_name);
  
  u8g2.enableUTF8Print();		// enable UTF8 support for the Arduino print() function
  u8g2.setFontDirection(0);
  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_unifont_t_container);

  if ( buf_container[0].length() == 0) {
    u8g2.setCursor(30, 30);    
    u8g2.print("无容器记录");
  }
  else {
    for (u8 i= 0; i < MAX_CONTAINER; i++){
      if (buf_container[i].length() == 0) 
        break;
      u8g2.setCursor(0, 15*(i+1));
      String numString = String(i+1);
      u8g2.print(numString + "." + buf_container[i]);
      Serial.println("." + buf_container[i] + " is shown.");
    }
  }
  u8g2.sendBuffer();
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
  //readFile(info_file);

  u8g2.begin();
  showContainerMenu(info_file);
  pinMode(REL_PIN, OUTPUT);
  digitalWrite(REL_PIN, LOW);
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
    showContainerMenu(info_file);
  }

  
  char key = keypad.getKey();

  if (key){
    Serial.println(key);
    int line_num = atoi(&key);
    if (line_num <= container_num){
      String container = buf_container[line_num-1]; // Index is zero based.
      String volumn = container.substring(container.indexOf(' ')+1, container.length()-1); // remove the last "L"
      
      Serial.println(container);
      Serial.println(volumn);
      
      interval = (unsigned long)(volumn.toFloat()*1000/15); // Suppose the watering is 15ml/s
      Serial.print("interval is set: ");
      Serial.println(interval, DEC);
      triggerMillis =  millis();
      wateringSta = true;
      digitalWrite(REL_PIN, HIGH);
    }
  }

  unsigned long currentMillis = millis();
  // Serial.println(currentMillis - triggerMillis, DEC);
  if ((interval != 0) & (currentMillis - triggerMillis >= interval)) {
    interval = 0;
    Serial.println("Timeout!!");
    
    Serial.print("currentMillis - triggerMillis = ");
    Serial.println(currentMillis - triggerMillis);
    wateringSta = false;
    
    digitalWrite(REL_PIN, LOW);
  } 
  delay(10);
}


