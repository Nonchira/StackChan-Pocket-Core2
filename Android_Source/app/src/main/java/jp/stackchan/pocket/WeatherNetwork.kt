package jp.stackchan.pocket

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Bind weather DNS and HTTPS only. Never rebind the process or the Core2 WebSocket. */
class WeatherNetwork(context: Context) {
    private val manager=context.getSystemService(ConnectivityManager::class.java)
    private val resolver=context.contentResolver
    private fun usable(network:Network):Boolean {
        val caps=manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    fun fetch(url:String,onRoute:(String)->Unit):String {
        val networks=manager.allNetworks.filter(::usable).sortedBy { if(it==manager.activeNetwork) 0 else 1 }
        var previous:Exception?=null
        for(network in networks) {
            try { return read(network,url,onRoute) } catch(e:Exception) { previous=e }
        }
        if(android.provider.Settings.Global.getInt(resolver,android.provider.Settings.Global.AIRPLANE_MODE_ON,0)!=0) {
            throw IOException("機内モードです。利用できるインターネットWi-Fiがないため保存済み予報を使用します",previous)
        }
        // Android may stop mobile data when the robot Wi-Fi is selected. Request it
        // temporarily, and retain the callback until the HTTPS response is consumed.
        val available=CompletableFuture<Network>()
        val callback=object:ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities) {
                if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) available.complete(network)
            }
            override fun onUnavailable() { available.completeExceptionally(IOException("利用できるモバイル回線がありません")) }
        }
        var registered=false
        try {
            val request=NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            manager.requestNetwork(request,callback,8000); registered=true
            val network=available.get(9,TimeUnit.SECONDS)
            return read(network,url,onRoute)
        } catch(e:Exception) {
            throw IOException(previous?.let { "天気サーバー取得失敗: ${TaskFailure.message(it,false)}" }
                ?: "インターネット回線を確保できません。モバイルデータまたはネット接続のあるWi-Fiを確認してください",e)
        } finally { if(registered)runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    private fun read(network:Network,url:String,onRoute:(String)->Unit):String {
        val caps=manager.getNetworkCapabilities(network)
        val route=when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)==true -> "モバイル回線"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true -> "インターネットWi-Fi"
            else -> "インターネット回線"
        }
        onRoute(route)
        val client=OkHttpClient.Builder().socketFactory(network.socketFactory)
            .dns(object:okhttp3.Dns {
                override fun lookup(hostname:String)=network.getAllByName(hostname).toList()
            })
            .connectionPool(ConnectionPool(0,1,TimeUnit.SECONDS))
            .connectTimeout(5,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).build()
        try {
            return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if(!response.isSuccessful)throw IOException("HTTP ${response.code}")
                val body=response.body ?: throw IOException("天気データが空です")
                body.byteStream().use { stream ->
                    val data=stream.readNBytes(128*1024+1)
                    require(data.size<=128*1024) { "天気データが大きすぎます" }
                    String(data,Charsets.UTF_8)
                }
            }
        } finally { client.connectionPool.evictAll() }
    }
}
