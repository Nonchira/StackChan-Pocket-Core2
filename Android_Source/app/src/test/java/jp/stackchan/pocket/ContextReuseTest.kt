package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class ContextReuseTest {
 private val history=listOf(ConversationMemory.Turn("質問","回答"))
 @Test fun completeMatchingHistoryIsReusable() { assertTrue(ContextReuse.allowed(true,false,true,history,history,1,1)) }
 @Test fun cancelledChangedOrForgottenHistoryMustRebuild() {
  assertFalse(ContextReuse.allowed(true,false,false,history,history,1,1))
  assertFalse(ContextReuse.allowed(true,false,true,history,emptyList(),1,1))
  assertFalse(ContextReuse.allowed(true,false,true,history,history,1,2))
  assertFalse(ContextReuse.allowed(true,true,true,history,history,1,1))
  assertFalse(ContextReuse.allowed(false,false,true,history,history,1,1))
 }
 @Test fun displayAndChunksPreserveOrder() {
  val h=ChatHistory();val id=h.add("assistant","はい、");h.append(id,"説明します。");val e=h.snapshot().entries.single()
  assertEquals("はい、説明します。",e.text);assertEquals(listOf("はい、","説明します。"),e.parts)
 }
}
