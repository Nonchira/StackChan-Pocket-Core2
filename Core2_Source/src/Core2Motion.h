#pragma once
#include <Arduino.h>
#include <M5Unified.h>
#include <math.h>
#include <Preferences.h>

// PORT A SG90 geometry from AI2 + Ataru: X=33, Y=32, center 90/85.
// Dedicated LEDC timer 0, channels 0/1. Core2 uses PMIC backlight and I2S audio.
class Core2Motion {
 public:
  void begin() {
    Preferences p;if(p.begin("servo_trim",true)){int v=p.getInt("xy",0);trimX=constrain((v&255)-10,-10,10);trimY=constrain(((v>>8)&255)-10,-10,10);if(!p.isKey("xy"))trimX=trimY=0;p.end();}
    M5.Ex_I2C.release();
    ledcSetup(0,50,16);ledcSetup(1,50,16);
    ledcAttachPin(33,0);ledcAttachPin(32,1);write();
    next=millis()+2500;
  }
  bool calibrating()const{return calibration;}
  int trim(bool vertical)const{return vertical?trimY:trimX;}
  void beginCalibration(){oldX=trimX;oldY=trimY;command("center");calibration=true;}
  void adjustTrim(bool vertical,int delta){int& value=vertical?trimY:trimX;value=constrain(value+delta,-10,10);write();quietUntil=millis()+500;}
  void resetTrim(){trimX=trimY=0;write();quietUntil=millis()+500;}
  bool saveTrim(){Preferences p;if(!p.begin("servo_trim",false))return false;bool ok=p.putInt("xy",(trimX+10)|((trimY+10)<<8))==sizeof(int32_t);p.end();if(ok){oldX=trimX;oldY=trimY;}return ok;}
  void endCalibration(){trimX=oldX;trimY=oldY;calibration=false;cancel();write();}
  bool command(const String& name) {
    cancel();doneOk=false;seqCount=0;seqIndex=0;
    auto add=[&](float a,float b){ seqX[seqCount]=a;seqY[seqCount++]=b; };
    if(name=="right")add(75,85);else if(name=="left")add(105,85);
    else if(name=="up")add(90,75);else if(name=="down")add(90,95);
    else if(name=="center")add(90,85);
    else if(name=="tilt")add(100,78);
    else if(name=="nod") { add(90,75);add(90,95);add(90,85); }
    else if(name=="dance") { add(80,80);add(100,90);add(80,90);add(100,80);add(90,85); }
    else return false;
    manual=true;next=millis();return true;
  }
  void cancel() { manual=false;moving=false;seqCount=0;next=millis()+3000;doneOk=false; }
  bool pending()const { return manual; }
  bool succeeded()const { return doneOk; }
  void update(unsigned long now,bool allow) {
    if(!allow) { if(manual)cancel();moving=false;next=now+600;return; }
    if(!moving && (!calibration || manual) && static_cast<int32_t>(now-next)>=0) {
      fromX=x;fromY=y;
      if(manual) { toX=seqX[seqIndex];toY=seqY[seqIndex++]; }
      else { toX=90+random(-15,16);toY=85+random(-10,11); }
      duration=max(400UL,static_cast<unsigned long>(max(fabsf(toX-x),fabsf(toY-y))*2000.0f/30.0f));
      start=now;moving=true;
    }
    if(!moving || now-last<20)return;
    last=now;float t=min(1.0f,float(now-start)/duration);
    float ease=t<0.5f?2*t*t:1-powf(-2*t+2,2)/2;
    x=fromX+(toX-fromX)*ease;y=fromY+(toY-fromY)*ease;
    write();quietUntil=now+500;
    if(t>=1) {
      moving=false;
      if(manual && seqIndex<seqCount)next=now+150;
      else { if(manual) { manual=false;doneOk=true;next=now+12000; } else next=now+random(1500,4000); }
    }
  }
  bool quiet(unsigned long now) const { return static_cast<int32_t>(quietUntil-now)>0; }
  float gazeX()const{return (x-90)/15.0f;}
  float gazeY()const{return (y-85)/10.0f;}
 private:
  bool calibration=false;int trimX=0,trimY=0,oldX=0,oldY=0;
  bool manual=false,doneOk=false;
  float seqX[6],seqY[6];int seqCount=0,seqIndex=0;
  float x=90,y=85,fromX=90,fromY=85,toX=90,toY=85;
  unsigned long start=0,last=0,next=0,duration=400,quietUntil=0;
  bool moving=false;
  void write() {
    // Same pulse endpoints as the original ServoEasing defaults, restricted angles.
    ledcWrite(0,uint32_t((544+(2400-544)*constrain(x+trimX,75.0f,105.0f)/180.0f)*65535.0f/20000.0f));
    ledcWrite(1,uint32_t((544+(2400-544)*constrain(y+trimY,75.0f,95.0f)/180.0f)*65535.0f/20000.0f));
  }
};
