package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class ContextRolloverTest {
 @Test fun rollingWindowRebuildsOnceThenReuses() {
  val m=ConversationMemory(); var cached=emptyList<ConversationMemory.Turn>();val reused=mutableListOf<Boolean>()
  for(i in 1..13) {
   reused.add(ContextReuse.allowed(true,false,i>1,cached,m.snapshot(),1,1))
   cached=m.snapshot()+ConversationMemory.Turn("q$i","a$i")
   m.remember("q$i","a$i",true)
   assertTrue(m.snapshot().size<=6)
  }
  assertEquals(listOf(1,8,11),reused.indices.filter { !reused[it] }.map { it+1 })
  assertEquals(listOf("q10","q11","q12","q13"),m.snapshot().map { it.user })
 }
 @Test fun characterLimitAlsoLeavesHeadroom() {
  val m=ConversationMemory(6,18)
  for(i in 1..5)m.remember("aa","bb",true)
  assertEquals(3,m.snapshot().size)
 }
 @Test fun oversizedLatestTurnAndResetCannotReuse() {
  val m=ConversationMemory(6,10);m.remember("a","b",true);val old=m.snapshot()
  m.remember("long message","response",true);assertTrue(m.snapshot().isEmpty())
  assertFalse(ContextReuse.allowed(true,false,true,old,m.snapshot(),1,1))
 }
 @Test fun disabledKeepsOriginalSixTurnRollingWindow() {
  val m=ConversationMemory();for(i in 1..8)m.remember("q$i","a$i",false)
  assertEquals(listOf("q3","q4","q5","q6","q7","q8"),m.snapshot().map { it.user })
 }
 @Test fun modeChangesAndCancellationStillRebuild() {
  val h=listOf(ConversationMemory.Turn("q","a"))
  assertFalse(ContextReuse.allowed(true,false,false,h,h,1,1))
  assertFalse(ContextReuse.allowed(true,false,true,h,h,1,2))
  assertTrue(ContextReuse.reason(true,false,true,h,emptyList(),1,1).contains("履歴"))
 }
}
