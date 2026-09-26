package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class PlaybackAuditTest {
 private fun report(id:String)=JSONObject().put("requestId",id).put("pcmBytesReceived",32000).put("pcmBytesAccepted",31000).put("pcmBytesDropped",1000).put("underflowResets",2)
 private val sent=PlaybackAudit.Sent(1,32000,1000,41,1280)
 @Test fun matchesAndConsumesOnlyExpectedId(){val a=PlaybackAudit();a.expect("a",sent);assertFalse(a.accept(report("wrong"),1));assertTrue(a.accept(report("a"),1));assertFalse(a.accept(report("a"),1));assertTrue(a.summary().contains("破棄 1000B"))}
 @Test fun obsoleteReplyIsRejected(){val a=PlaybackAudit();a.expect("a",sent);assertFalse(a.accept(report("a"),2));assertFalse(a.summary().contains("破棄 1000B"))}
 @Test fun cancellationRemovesOutstandingRequests(){val a=PlaybackAudit();a.expect("a",sent);a.cancel();assertFalse(a.accept(report("a"),1));assertTrue(a.summary().contains("取りこぼし 1 件"))}
 @Test fun historyAndOutstandingRequestsAreBounded(){val a=PlaybackAudit();repeat(30){a.expect("$it",sent)};assertFalse(a.accept(report("0"),1));assertTrue(a.summary().contains("未応答 12件"));for(i in 18..29)assertTrue(a.accept(report("$i"),1));assertTrue(a.summary().contains("区間12"));assertFalse(a.summary().contains("区間13"))}
 @Test fun unknownCountersAreNotReportedAsZero(){val a=PlaybackAudit();a.expect("a",sent);a.accept(JSONObject().put("requestId","a"),1);assertTrue(a.summary().contains("差分 不明B"))}
}
