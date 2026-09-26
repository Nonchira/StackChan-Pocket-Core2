package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class StreamingHistoryTest {
    @Test fun sentencesStayInOneBubble() { val h=ChatHistory();val id=h.add("assistant","最初。");h.append(id,"次。");assertEquals(1,h.snapshot().entries.size);assertEquals("最初。次。",h.snapshot().entries.single().text) }
    @Test fun clearingLogCannotResurrectPartialAnswer() { val h=ChatHistory();val id=h.add("assistant","最初。");h.clear();h.append(id,"次。");assertTrue(h.snapshot().entries.isEmpty()) }
}
