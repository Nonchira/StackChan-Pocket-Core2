package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test

class ChatHistoryTest {
    @Test fun boundedHistoryRetainsNewestAndSnapshotsStayStable() {
        val history=ChatHistory(2)
        history.add("user","one",1)
        val first=history.snapshot()
        history.add("assistant","two",2)
        history.add("user","three",3)
        assertEquals(listOf("two","three"),history.snapshot().entries.map { it.text })
        assertEquals(listOf("one"),first.entries.map { it.text })
    }
    @Test fun clearNotifiesUiAndDoesNotReuseMessageIds() {
        val history=ChatHistory()
        history.add("user","hello")
        val before=history.snapshot()
        history.clear()
        assertTrue(history.snapshot().entries.isEmpty())
        assertTrue(history.snapshot().revision>before.revision)
        history.add("assistant","hello")
        assertTrue(history.snapshot().entries.single().id>before.entries.single().id)
    }
    @Test fun emptyMessagesDoNotCreatePhantomUpdates() {
        val history=ChatHistory()
        val before=history.snapshot()
        history.add("user"," \n ")
        assertEquals(before,history.snapshot())
    }
}
