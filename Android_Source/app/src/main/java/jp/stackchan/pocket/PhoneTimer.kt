package jp.stackchan.pocket

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.SystemClock
import android.provider.Settings
import java.util.UUID

/** An immutable alarm identity prevents a canceled/replaced alarm from notifying. */
data class TimerRecord(val id:String,val due:Long,val boot:Int) {
    fun remaining(now:Long,currentBoot:Int):Long? = if(id.isBlank() || boot!=currentBoot) null else ((due-now).coerceAtLeast(0)+999)/1000
    fun accepts(candidate:String,currentBoot:Int)=id.isNotBlank() && id==candidate && boot==currentBoot
}
object PhoneTimer {
    private const val CHANNEL="timer_finished"
    private const val NOTICE=200
    private fun prefs(c:Context)=c.getSharedPreferences("phone_timer",Context.MODE_PRIVATE)
    private fun boot(c:Context)=Settings.Global.getInt(c.contentResolver,Settings.Global.BOOT_COUNT,0)
    private fun record(c:Context):TimerRecord { val p=prefs(c);return TimerRecord(p.getString("id","")!!,p.getLong("due",0),p.getInt("boot",-1)) }
    private fun manager(c:Context)=c.getSystemService(AlarmManager::class.java)
    private fun notifications(c:Context)=c.getSystemService(NotificationManager::class.java)
    fun channel(c:Context) {
        notifications(c).createNotificationChannel(NotificationChannel(CHANNEL,"タイマー終了",NotificationManager.IMPORTANCE_HIGH).apply {
            description="会話中やCore2未接続でもタイマー終了を知らせます"
            enableVibration(true);vibrationPattern=longArrayOf(0,400,200,400)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        })
    }
    fun problem(c:Context):String? {
        channel(c)
        if(!notifications(c).areNotificationsEnabled())return "設定のタイマーから通知を許可してください"
        if(notifications(c).getNotificationChannel(CHANNEL).importance==NotificationManager.IMPORTANCE_NONE)return "タイマー終了の通知を有効にしてください"
        if(!manager(c).canScheduleExactAlarms())return "設定のタイマーからアラームとリマインダーを許可してください"
        return null
    }
    private fun pending(c:Context,id:String)=PendingIntent.getBroadcast(c,200,
        Intent(c,TimerReceiver::class.java).setAction("jp.stackchan.pocket.TIMER").setData(android.net.Uri.parse("stackchan-timer:$id")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun start(c:Context,seconds:Long):Boolean {
        require(seconds in 1..3600)
        check(problem(c)==null) { problem(c)!! }
        val old=record(c);val replaced=old.remaining(SystemClock.elapsedRealtime(),boot(c))!=null
        val next=TimerRecord(UUID.randomUUID().toString(),SystemClock.elapsedRealtime()+seconds*1000,boot(c))
        val alarm=pending(c,next.id)
        manager(c).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,next.due,alarm)
        if(!prefs(c).edit().putString("id",next.id).putLong("due",next.due).putInt("boot",next.boot).remove("announce").commit()) {
            manager(c).cancel(alarm);alarm.cancel();error("タイマーを保存できませんでした")
        }
        if(old.id.isNotBlank()){val previous=pending(c,old.id);manager(c).cancel(previous);previous.cancel()}
        notifications(c).cancel(NOTICE)
        return replaced
    }
    @Synchronized fun cancel(c:Context) {
        val old=record(c)
        check(prefs(c).edit().clear().commit()) { "タイマーの取消を保存できませんでした" }
        if(old.id.isNotBlank()){val alarm=pending(c,old.id);manager(c).cancel(alarm);alarm.cancel()}
        notifications(c).cancel(NOTICE)
    }
    @Synchronized fun remaining(c:Context)=record(c).remaining(SystemClock.elapsedRealtime(),boot(c))
    @Synchronized fun fire(c:Context,id:String) {
        if(!record(c).accepts(id,boot(c)))return
        if(!prefs(c).edit().remove("id").putLong("announce",SystemClock.elapsedRealtime()).commit())return
        channel(c)
        val open=PendingIntent.getActivity(c,200,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("スタックチャン：タイマー終了").setContentText("設定した時間になりました。")
            .setCategory(Notification.CATEGORY_ALARM).setContentIntent(open).setAutoCancel(true).build()
        notifications(c).notify(NOTICE,notification)
    }
    @Synchronized fun takeAnnouncement(c:Context):Boolean {
        val p=prefs(c);val whenFired=p.getLong("announce",0)
        if(whenFired==0L)return false
        p.edit().remove("announce").apply()
        return boot(c)==p.getInt("boot",-1) && SystemClock.elapsedRealtime()-whenFired in 0..60000
    }
}
class TimerReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action=="jp.stackchan.pocket.TIMER")PhoneTimer.fire(context,intent.data?.schemeSpecificPart ?: "")
    }
}
