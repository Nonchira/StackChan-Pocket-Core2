#pragma once
#include <Arduino.h>
#include <ArduinoJson.h>
#include <M5Unified.h>
#include <Preferences.h>
#include <functional>

// User's cloud edition button layout, shared with the independent Android app.
class Core2Compat {
 public:
  std::function<bool(const char*)> send;
  std::function<void()> stopAudio;
  std::function<void()> click;
  std::function<void(int)> expression;
  std::function<void(int)> mouth;
  std::function<bool()> canTouch;
  std::function<bool(const String&)> motionStart;
  std::function<bool()> motionPending,motionSucceeded;
  std::function<void()> motionCancel;
  std::function<bool(int)> storeVolume;
  std::function<int()> getVolume;
  std::function<void(int)> setVolume;
  std::function<void()> calibrationBegin,calibrationEnd,calibrationReset;
  std::function<void(bool,int)> calibrationAdjust;
  std::function<int(bool)> calibrationValue;
  std::function<bool()> calibrationSave;
  bool batteryVisible=false;
  std::function<void(bool)> batteryVisibility;
  bool usbMode=false;
  bool servoEnabled=true;
  bool conversationReady=false;
  std::function<bool()> playing;
  std::function<void(bool)> coverDisplay;
  bool wake = false;
  bool phoneMic = false;
  int region = 0;
  int page = 0;
  int item = 0;
  bool active() const { return page != 0 || noticeUntil != 0; }
  void begin() {
    Preferences p; if (p.begin("pocket", true)) {
      batteryVisible=p.getBool("batteryView",false);
      usbMode=p.getBool("usb",false);
      servoEnabled=p.getBool("servo",true);
      region = p.getUChar("region_v2", 0); wake = p.getBool("wake", false);
      phoneMic = p.getBool("phoneMic", false); p.end();
    }
    if (region > 1) region = 0;
  }
  bool command(JsonDocument& j) {
    String type = j["type"] | "";
    if(type=="compat.control") {
      String id=j["requestId"] | "",op=j["op"] | "",value=j["value"] | "";
      if(id.length()==0 || id.length()>100)return true;
      if(op=="motion") {
        if(moveId.length() || !servoEnabled || active() || !motionStart || !motionStart(value))reply(id,false,-1,"サーボ操作を開始できません。メニューとサーボ設定を確認してください");
        else moveId=id;
      } else if(op=="battery") { int n=M5.Power.getBatteryLevel();reply(id,n>=0 && n<=100,n,"電池残量を取得できません"); }
      else if(op=="volume.set" || op=="volume.delta") {
        int n=value.toInt();if(op=="volume.delta" && getVolume)n=getVolume()+n;
        n=constrain(n,0,100);bool ok=storeVolume && storeVolume(n);reply(id,ok,getVolume?getVolume():-1,"音量を保存できませんでした");if(ok)reportVolume();
      } else if(op=="region") {
        int n=-1;for(int i=0;i<2;++i)if(value==ids[i])n=i;
        bool ok=false;Preferences p;if(n>=0 && p.begin("pocket",false)) { ok=p.putUChar("region_v2",n)==1;p.end(); }
        if(ok)region=n;reply(id,ok,n,"地点を保存できませんでした");
      } else reply(id,false,-1,"未対応の操作です");
      return true;
    }
    if(type=="compat.click") {
      if(send)send("{\"type\":\"compat.feedback\"}");
      if(click)click();return true;
    }
    if(type=="compat.expression") { if(expression)expression(j["value"] | 0);return true; }
    if(type=="compat.mouth") {
      if(mouth && j["envelope"].is<int>())mouth(constrain(j["envelope"].as<int>(),0,32768));
      return true;
    }
    if(type=="compat.volume.get" || type=="compat.volume.set") {
      if(type=="compat.volume.set" && j["percent"].is<int>() && setVolume)setVolume(constrain(j["percent"].as<int>(),0,100));
      reportVolume();if(page==4)draw();return true;
    }
    if(type=="compat.ready") { conversationReady=j["listening"] | false;return true; }
    if (type == "compat.drain") { waitingPlayback=true; playbackId=String(j["requestId"] | "").substring(0,80); return true; }
    if (type == "compat.stop") { cancelMotion();if(stopAudio) stopAudio(); return true; }
    if (type != "compat.sync") return false;
    String r = j["region"] | "osaka";
    for(int i=0;i<2;++i) if(r==ids[i]) region=i;
    bool oldWake=wake;
    wake = j["wake"] | false;
    phoneMic = String(j["mic"] | "core2") == "phone";
    save(); if(page)draw();
    else if(oldWake!=wake) {
      if(coverDisplay)coverDisplay(true);
      noticeUntil=millis()+1500; M5.Display.fillScreen(TFT_BLACK);
      M5.Display.setFont(&fonts::efontJA_16);M5.Display.setTextColor(TFT_WHITE,TFT_BLACK);
      M5.Display.setCursor(20,90);M5.Display.print(wake?"ウェイクワード有効":"ウェイクワード無効");
    }
    return true;
  }
  void update(unsigned long now) {
    if(moveId.length() && motionPending && !motionPending()) {
      reply(moveId,motionSucceeded && motionSucceeded(),0,"動作が中断されました");moveId="";
    }
    if(waitingPlayback && playing && !playing()) {
      waitingPlayback=false;
      JsonDocument j; j["type"]="compat.playback.done"; j["requestId"]=playbackId;
      String text;serializeJson(j,text);if(send)send(text.c_str());
    }
    if(noticeUntil && static_cast<int32_t>(now-noticeUntil)>=0) {
      noticeUntil=0; M5.Display.fillScreen(TFT_BLACK); M5.Display.setFont(&fonts::Font0);
      if(coverDisplay)coverDisplay(false);
    }
    if (waitRelease) { if(!M5.BtnB.isPressed())waitRelease=false; return; }
    if(M5.BtnA.wasPressed() || M5.BtnB.wasPressed() || M5.BtnC.wasPressed()) {
      if(send)send("{\"type\":\"compat.feedback\"}");
      if(click)click();
    }
    if(page==6 || page==7) {
      if(M5.BtnA.wasPressed() || M5.BtnC.wasPressed()) { if(calibrationAdjust)calibrationAdjust(page==7,M5.BtnA.wasPressed()?-1:1);draw(); }
      if(M5.BtnB.wasPressed()){item=page==6?0:1;page=5;draw();waitRelease=true;}return;
    }
    if(page==4) {
      if(M5.BtnA.wasPressed() || M5.BtnC.wasPressed()) {
        if(getVolume && setVolume)setVolume(constrain(getVolume()+(M5.BtnA.wasPressed()?-5:5),0,100));
        reportVolume();draw();
      }
      if(M5.BtnB.wasPressed()) { page=1;item=6;draw();waitRelease=true; }
      return;
    }
    if (page) {
      int count=page==1?12:page==5?5:3;
      if(M5.BtnA.wasPressed()) { item=(item+count-1)%count; draw(); }
      if(M5.BtnC.wasPressed()) { item=(item+1)%count; draw(); }
      if(M5.BtnB.wasPressed()) { select(); waitRelease=true; }
      return;
    }
    const auto touch=M5.Touch.getDetail();
    // Match original box_stt: full display width, upper 60 pixels.
    if(!active() && canTouch && canTouch() && touch.wasClicked() &&
       touch.base_x>=0 && touch.base_x<M5.Display.width() && touch.x>=0 && touch.x<M5.Display.width() &&
       touch.base_y>=0 && touch.base_y<60 && touch.y>=0 && touch.y<60 &&
       now-lastFaceTap>=600) {
      lastFaceTap=now;
      if(send)send("{\"type\":\"compat.feedback\"}");
      if(click)click();
      emit("touch.listen");return;
    }
    // Ataru face touch regions: left = monologue, right = battery, centre = servo.
    if(!active() && canTouch && canTouch() && touch.wasClicked() && now-lastFaceTap>=600) {
      auto inside=[&](int x,int y,int w,int h) { return touch.x>=x && touch.x<x+w && touch.y>=y && touch.y<y+h && touch.base_x>=x && touch.base_x<x+w && touch.base_y>=y && touch.base_y<y+h; };
      const char* action=nullptr;
      if(inside(0,100,40,60))action="monologue.toggle";
      else if(inside(280,100,40,60))action="battery";
      else if(inside(80,120,80,80)) { servoEnabled=!servoEnabled;save();action="servo.local"; }
      if(action) {
        lastFaceTap=now;if(send)send("{\"type\":\"compat.feedback\"}");if(click)click();
        if(String(action)!="servo.local")emit(action);return;
      }
    }
    if(M5.BtnB.wasPressed()) { longHandled=false; pressedAt=now; }
    if(!longHandled && M5.BtnB.pressedFor(2000)) {
      longHandled=true; page=1; item=0; noticeUntil=0;
      if(coverDisplay)coverDisplay(true);
      Serial.println("[core2.menu] open");
      if(stopAudio)stopAudio(); emit("stop"); draw(); waitRelease=true; return;
    }
    if(M5.BtnB.wasReleased()) {
      if(!longHandled && now-pressedAt<2000)emit("weather");
      longHandled=false;
    }
    if(M5.BtnA.wasPressed())emit("wake.toggle");
    if(M5.BtnC.wasPressed())emit("battery");
  }
 private:
  String moveId;
  void reply(const String& id,bool ok,int value,const char* error) {
    JsonDocument j;j["type"]="compat.control.result";j["requestId"]=id;j["ok"]=ok;j["value"]=value;if(!ok)j["error"]=error;
    String s;serializeJson(j,s);if(send)send(s.c_str());
  }
  void cancelMotion() { if(motionCancel)motionCancel();if(moveId.length()) { reply(moveId,false,-1,"動作を停止しました");moveId=""; } }
  void reportVolume() {
    if(!getVolume)return;
    JsonDocument j;j["type"]="compat.volume";j["percent"]=getVolume();
    String text;serializeJson(j,text);if(send)send(text.c_str());
  }
  const char* ids[2]={"osaka","tokyo"};
  const char* names[2]={"大阪府","東京都"};
  bool longHandled=false,waitRelease=false,waitingPlayback=false;
  String playbackId;
  unsigned long pressedAt=0,noticeUntil=0,lastFaceTap=0;
  void save() {
    Preferences p; if(p.begin("pocket",false)) {
      p.putBool("servo",servoEnabled);p.putUChar("region_v2",region);p.putBool("wake",wake);p.putBool("phoneMic",phoneMic);p.end();
    }
  }
  void emit(const char* action) {
    JsonDocument j; j["type"]="compat.action";j["action"]=action;
    j["region"]=ids[region];j["mic"]=phoneMic?"phone":"core2";
    if(String(action)=="battery")j["percent"]=M5.Power.getBatteryLevel();
    String message;serializeJson(j,message);
    if(!send || !send(message.c_str())) {
      // A local menu must remain operable even if the phone is disconnected.
      if(page) { Serial.println("[core2.menu] phone unavailable; keep menu open"); return; }
      page=0;noticeUntil=millis()+2500;
      if(coverDisplay)coverDisplay(true);
      M5.Display.fillScreen(TFT_BLACK);M5.Display.setFont(&fonts::efontJA_16);
      M5.Display.setTextColor(TFT_WHITE,TFT_BLACK);M5.Display.setCursor(12,80);
      M5.Display.print("スマホアプリに接続してください");
    }
  }
  void close() {
    if(page>=5 && page<=7 && calibrationEnd)calibrationEnd();
    page=0;noticeUntil=0;M5.Display.fillScreen(TFT_BLACK);M5.Display.setFont(&fonts::Font0);
    if(coverDisplay)coverDisplay(false);
    Serial.println("[core2.menu] close");
  }
  void select() {
    if(page==8){
      if(item==2){page=1;item=9;draw();return;}
      Preferences p;bool ok=false;if(p.begin("pocket",false)){ok=p.putBool("usb",item==1)==1;p.end();}
      if(ok){if(stopAudio)stopAudio();M5.Display.fillScreen(TFT_BLACK);M5.Display.setCursor(12,90);M5.Display.print("通信方式を保存しました。再起動します");delay(700);ESP.restart();}
      else {draw();M5.Display.setCursor(12,185);M5.Display.print("保存に失敗しました");}return;
    }
    if(page==5){
      if(item<2){page=item==0?6:7;draw();return;}
      if(item==2){if(calibrationSave && calibrationSave()){close();}else {draw();M5.Display.setCursor(8,190);M5.Display.print("保存失敗：もう一度試してください");}return;}
      if(item==3){if(calibrationReset)calibrationReset();draw();return;}
      close();return;
    }
    if(page==1) {
      switch(item) {
        case 0:close();emit("wake.register");return;
        case 1:page=2;item=region;draw();return;
        case 2:page=3;item=phoneMic?1:0;draw();return;
        case 3:close();emit("listen");return;
        case 4:close();emit("stop");return;
        case 5:servoEnabled=!servoEnabled;save();draw();return;
        case 6:page=4;draw();return;
        case 7:close();emit("news");return;
        case 8:if(!servoEnabled)return;if(calibrationBegin)calibrationBegin();page=5;item=0;draw();return;
        case 9:page=8;item=usbMode?1:0;draw();return;
        case 10: {
          Preferences p; bool next=!batteryVisible,ok=false;
          if(p.begin("pocket",false)){ok=p.putBool("batteryView",next)==1;p.end();}
          if(ok){batteryVisible=next;if(batteryVisibility)batteryVisibility(next);}
          draw();
          if(!ok){M5.Display.setCursor(8,190);M5.Display.print("保存に失敗しました");}
          return;
        }
        default:close();return;
      }
    }
    if(page==2) {
      if(item==2) { page=1;item=1;draw();return; }
      region=item;save();close();emit("region");return;
    }
    if(item==2) { page=1;item=2;draw();return; }
    phoneMic=item==1;save();close();emit("mic");
  }
  void draw() {
    if(coverDisplay)coverDisplay(true);
    M5.Display.fillScreen(TFT_BLACK);M5.Display.setFont(&fonts::efontJA_16);
    M5.Display.setTextSize(1);M5.Display.setTextColor(TFT_WHITE,TFT_BLACK);
    if(page==8){
      M5.Display.setCursor(8,6);M5.Display.print("通信方式（変更後に再起動）");
      const char* rows[]={"Wi-Fi","USBシリアル（Wi-Fi停止）","戻る"};
      for(int i=0;i<3;++i){int y=45+i*35;uint16_t bg=i==item?TFT_BLUE:TFT_BLACK;M5.Display.fillRect(4,y,312,32,bg);M5.Display.setTextColor(TFT_WHITE,bg);M5.Display.setCursor(10,y+7);M5.Display.print(rows[i]);if(i==(usbMode?1:0))M5.Display.print(" *");}
      M5.Display.setTextColor(TFT_WHITE,TFT_BLACK);M5.Display.setCursor(8,216);M5.Display.print("A:前へ  B:決定  C:次へ");return;
    }
    if(page>=5 && page<=7){
      M5.Display.setCursor(8,6);M5.Display.print("サーボ正面補正（1度刻み）");
      int x=calibrationValue?calibrationValue(false):0,y=calibrationValue?calibrationValue(true):0;
      if(page==6 || page==7){
        M5.Display.setCursor(12,60);M5.Display.print(page==6?"左右の補正":"上下の補正");
        M5.Display.setCursor(70,95);M5.Display.setTextSize(3);M5.Display.printf("%+d",page==6?x:y);M5.Display.setTextSize(1);
        M5.Display.setCursor(8,160);M5.Display.print("範囲 -10〜+10 / Bで調整メニュー");
        M5.Display.setCursor(8,216);M5.Display.print("A: −1度   B:戻る   C: ＋1度");return;
      }
      String rows[]={String("左右：")+x+"度",String("上下：")+y+"度","保存して終了","補正を0に戻す","保存せず終了"};
      for(int i=0;i<5;++i){int y0=35+i*28;uint16_t bg=i==item?TFT_BLUE:TFT_BLACK;M5.Display.fillRect(4,y0,312,26,bg);M5.Display.setTextColor(TFT_WHITE,bg);M5.Display.setCursor(10,y0+5);M5.Display.print(rows[i]);}
      M5.Display.setTextColor(TFT_WHITE,TFT_BLACK);M5.Display.setCursor(8,185);M5.Display.print("調整中は正面保持 / 保存で次回も反映");
      M5.Display.setCursor(8,216);M5.Display.print("A:前へ  B:決定  C:次へ");return;
    }
    if(page==4) {
      M5.Display.setCursor(12,16);M5.Display.print("スピーカー音量");
      M5.Display.setCursor(115,85);M5.Display.setTextSize(3);M5.Display.printf("%d%%",getVolume?getVolume():0);
      M5.Display.setTextSize(1);M5.Display.setCursor(12,158);M5.Display.print("0%で消音 / 自動保存");
      M5.Display.setCursor(12,216);M5.Display.print("A:小さく  B:戻る  C:大きく");return;
    }
    M5.Display.setCursor(8,6);M5.Display.print(page==1?"設定メニュー":page==2?"天気予報地点":"マイク選択");
    const char* main[]={"ウェイクワード登録","天気予報地点","マイク選択","会話開始","会話停止",servoEnabled?"サーボ: ON":"サーボ: OFF","音量","ニュース",servoEnabled?"サーボ正面補正":"サーボ正面補正（OFF）",usbMode?"通信方式：USB":"通信方式：Wi-Fi",batteryVisible?"バッテリー表示: ON":"バッテリー表示: OFF","閉じる"};
    int count=page==1?12:page==5?5:3;
    const int first=page==1?(item/8)*8:0;
    for(int i=first;i<count && (page!=1 || i<first+8);++i) {
      int y=30+(i-first)*(page==1?20:25);uint16_t bg=i==item?TFT_BLUE:TFT_BLACK;
      M5.Display.fillRect(4,y,312,page==1?19:24,bg);M5.Display.setTextColor(TFT_WHITE,bg);M5.Display.setCursor(10,y+(page==1?1:5));
      M5.Display.print(page==1?main[i]:page==2?(i==2?"戻る":names[i]):(i==0?"Core2のマイク":i==1?"スマホのマイク":"戻る"));
      if((page==2&&i==region)||(page==3&&i==(phoneMic?1:0)))M5.Display.print(" [選択中]");
    }
    M5.Display.setTextColor(TFT_WHITE,TFT_BLACK);M5.Display.setCursor(8,216);
    M5.Display.print("A:前へ  B:決定  C:次へ");
  }
};
