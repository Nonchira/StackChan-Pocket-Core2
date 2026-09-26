package jp.stackchan.pocket

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.SystemClock
import com.hoho.android.usbserial.driver.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class UsbRobotTransport(private val context:Context,private val listener:RobotTransport.Listener):RobotTransport {
    private val manager=context.getSystemService(UsbManager::class.java)
    private val closed=AtomicBoolean(false)
    private val opening=AtomicBoolean(false)
    private val tx=ArrayBlockingQueue<ByteArray>(96)
    private val queued=AtomicLong()
    private var sequence=0L
    private val nonce=UUID.randomUUID().toString()
    private val permissionAction="${context.packageName}.USB_PERMISSION.$nonce"
    private var permission:PendingIntent?=null
    private var registered=false
    @Volatile private var device:UsbDevice?=null
    @Volatile private var port:UsbSerialPort?=null
    @Volatile private var connection:UsbDeviceConnection?=null
    @Volatile private var verified=false
    @Volatile private var lastPong=0L
    private val receiver=object:BroadcastReceiver(){
        override fun onReceive(c:Context,i:Intent){
            val d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE,UsbDevice::class.java) ?: return
            if(d.deviceId!=device?.deviceId || closed.get())return
            when(i.action){
                permissionAction -> if(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false) && manager.hasPermission(d))open(d) else fail("USBの使用が許可されませんでした。再接続から許可してください")
                UsbManager.ACTION_USB_DEVICE_DETACHED -> fail("USBケーブルが外れました。接続して再接続を押してください")
            }
        }
    }
    override fun start(){
        try {
            val candidates=manager.deviceList.values.filter { (it.vendorId==0x10c4 && it.productId==0xea60) || (it.vendorId==0x1a86 && it.productId==0x55d4) }
            check(candidates.size==1){if(candidates.isEmpty()) "Core2のUSBが見つかりません。データ対応ケーブルとUSBホスト接続を確認してください" else "対象USB機器が複数あります。Core2を1台だけ接続してください"}
            val d=candidates.single();device=d
            context.registerReceiver(receiver,IntentFilter(permissionAction).apply { addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) },Context.RECEIVER_NOT_EXPORTED);registered=true
            if(manager.hasPermission(d))open(d) else {
                permission=PendingIntent.getBroadcast(context,0,Intent(permissionAction).setPackage(context.packageName),PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                manager.requestPermission(d,permission!!)
            }
        }catch(e:Exception){fail(e.message ?: "USB接続失敗")}
    }
    private fun open(d:UsbDevice){
        if(!opening.compareAndSet(false,true))return
        Thread({
            var ownedPort:UsbSerialPort?=null
            var ownedConnection:UsbDeviceConnection?=null
            try {
                val driver=UsbSerialProber.getDefaultProber().probeDevice(d) ?: error("このUSBシリアルのドライバが見つかりません")
                val conn=manager.openDevice(d) ?: error("USBを開けません。使用許可を確認してください")
                ownedConnection=conn
                synchronized(this){if(closed.get())return@Thread;connection=conn}
                val p=driver.ports.first();ownedPort=p
                synchronized(this){if(closed.get())return@Thread;port=p}
                p.open(conn);p.setParameters(921600,8,UsbSerialPort.STOPBITS_1,UsbSerialPort.PARITY_NONE)
                p.dtr=false;p.rts=false
                Thread.sleep(500)
                if(closed.get())return@Thread
                lastPong=SystemClock.elapsedRealtime()
                Thread({writeLoop(p)},"core2-usb-write").start()
                val decoder=SerialFrames.Decoder();val bytes=ByteArray(8192);var lastRx=SystemClock.elapsedRealtime()
                while(!closed.get()) {
                    // timeout=0 uses UsbRequest/requestWait instead of timed bulkTransfer.
                    // The driver documents data loss at high baud rates with <=200 ms
                    // timed reads. The independent writer's 12 s pong watchdog still
                    // closes/cancels this pending read on failure; user close does too.
                    val n=p.read(bytes,0)
                    if(SystemClock.elapsedRealtime()-lastRx>1000)decoder.reset()
                    if(n>0){lastRx=SystemClock.elapsedRealtime();for(frame in decoder.accept(bytes.copyOf(n))){
                        if(frame.type==1){
                            val value=frame.payload.toString(Charsets.UTF_8)
                            val j=runCatching { JSONObject(value) }.getOrNull()
                            if(j?.optString("type")=="pong" && j.optString("id")==nonce){
                                lastPong=SystemClock.elapsedRealtime()
                                if(!verified){verified=true;listener.opened()}
                            } else if(verified)listener.text(value)
                        }else if(frame.type==3 && verified)listener.audio(frame.payload)
                    }}
                }
            }catch(e:Exception){fail("USB受信・接続：${e.message}。USBを挿し直して再接続してください")}
            finally {runCatching { ownedPort?.close() };runCatching { ownedConnection?.close() }}
        },"core2-usb-read").start()
    }
    private fun writeLoop(p:UsbSerialPort){
        var pingAt=0L
        try {
            while(!closed.get()){
                val now=SystemClock.elapsedRealtime()
                check(now-lastPong<12000){"Core2から応答がありません。本体メニューの通信をUSBにしてください"}
                if(now>=pingAt){p.write(SerialFrames.encode(1,nextSequence(),JSONObject().put("type","ping").put("id",nonce).toString().toByteArray()),1000);pingAt=now+2000}
                val frame=tx.poll(50,java.util.concurrent.TimeUnit.MILLISECONDS)
                if(frame!=null){try { p.write(frame,1000) } finally { queued.addAndGet(-frame.size.toLong()) }}
            }
        }catch(e:Exception){fail("USB送信・応答確認：${e.message}。USBを挿し直して再接続してください")}
    }
    @Synchronized private fun nextSequence()=sequence++
    private fun enqueue(type:Int,value:ByteArray):Boolean {
        val accepted=synchronized(this) {
            if(closed.get() || !verified || value.size>SerialFrames.MAX)return false
            val frame=SerialFrames.encode(type,nextSequence(),value)
            queued.addAndGet(frame.size.toLong())
            if(tx.offer(frame))true else { queued.addAndGet(-frame.size.toLong());false }
        }
        // Never enter service cancellation while holding the transport monitor.
        if(!accepted)fail("USB送信が滞っています。再接続してください")
        return accepted
    }
    override fun sendText(value:String)=enqueue(1,value.toByteArray(Charsets.UTF_8))
    override fun sendAudio(value:ByteArray)=enqueue(2,value)
    override fun queuedBytes()=queued.get().coerceAtLeast(0)
    @Synchronized override fun discardAudio(){
        for(frame in tx.toTypedArray())if(frame[5]==2.toByte() && tx.remove(frame))queued.addAndGet(-frame.size.toLong())
    }
    private fun fail(message:String){if(closeOnce())listener.failed(message)}
    override fun close(){closeOnce()}
    private fun closeOnce():Boolean {
        val resources=synchronized(this) {
            if(!closed.compareAndSet(false,true))return false
            verified=false;tx.clear()
            val detached=Triple(port,connection,registered)
            port=null;connection=null;registered=false
            detached
        }
        // Native close may wait for reader/writer threads: no monitor is held here.
        permission?.cancel()
        if(resources.third)runCatching { context.unregisterReceiver(receiver) }
        runCatching { resources.first?.close() };runCatching { resources.second?.close() }
        return true
    }
}

