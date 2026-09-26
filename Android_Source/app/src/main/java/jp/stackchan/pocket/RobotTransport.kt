package jp.stackchan.pocket

import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString

interface RobotTransport {
    interface Listener {
        fun opened()
        fun text(value:String)
        fun audio(value:ByteArray)
        fun failed(message:String)
    }
    fun start()
    fun sendText(value:String):Boolean
    fun sendAudio(value:ByteArray):Boolean
    fun queuedBytes():Long
    fun discardAudio() {}
    fun close()
}
class WifiRobotTransport(private val client:OkHttpClient,private val host:String,private val listener:RobotTransport.Listener):RobotTransport {
    @Volatile private var socket:WebSocket?=null
    @Volatile private var closed=false
    override fun start() {
        socket=client.newWebSocket(Request.Builder().url("ws://$host:8080/").build(),object:WebSocketListener(){
            override fun onOpen(ws:WebSocket,response:Response){if(closed){ws.cancel();return};socket=ws;listener.opened()}
            override fun onMessage(ws:WebSocket,text:String){if(!closed)listener.text(text)}
            override fun onMessage(ws:WebSocket,bytes:ByteString){if(!closed)listener.audio(bytes.toByteArray())}
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){if(!closed)listener.failed(t.message ?: "Wi-Fi通信失敗")}
            override fun onClosed(ws:WebSocket,code:Int,reason:String){if(!closed)listener.failed("Wi-Fi切断")}
        })
    }
    override fun sendText(value:String)=!closed && socket?.send(value)==true
    override fun sendAudio(value:ByteArray)=!closed && socket?.send(value.toByteString())==true
    override fun queuedBytes()=socket?.queueSize() ?: Long.MAX_VALUE
    override fun close(){closed=true;socket?.cancel();socket=null}
}
