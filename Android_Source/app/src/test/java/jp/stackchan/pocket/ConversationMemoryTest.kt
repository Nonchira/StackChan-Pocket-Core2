package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test

class ConversationMemoryTest {
    @Test fun completedTurnsKeepUserAndAssistantRolesInOrder() {
        val m=ConversationMemory()
        m.remember("名前はシナです","シナさん、こんにちは")
        m.remember("好きな色は青です","青が好きなんですね")
        assertEquals("名前はシナです",m.snapshot()[0].user)
        assertEquals("青が好きなんですね",m.snapshot()[1].assistant)
    }
    @Test fun countLimitDropsWholeOldestExchange() {
        val m=ConversationMemory(2,100)
        m.remember("one","1");m.remember("two","2");m.remember("three","3")
        assertEquals(listOf("two","three"),m.snapshot().map { it.user })
    }
    @Test fun characterBudgetDropsWholeTurnsWithoutRoleFragments() {
        val m=ConversationMemory(6,10)
        m.remember("abc","def");m.remember("gh","ij")
        assertEquals(2,m.snapshot().size)
        m.remember("kl","mn")
        assertEquals(listOf("gh","kl"),m.snapshot().map { it.user })
    }
    @Test fun emptyOrFailedResponseIsNotRemembered() {
        val m=ConversationMemory();m.remember("test","")
        assertTrue(m.snapshot().isEmpty())
    }
    @Test fun resetClearsFutureContextButDoesNotMutateExistingSnapshot() {
        val m=ConversationMemory();m.remember("name","reply")
        val snapshot=m.snapshot();m.clear()
        assertTrue(m.snapshot().isEmpty());assertEquals(1,snapshot.size)
    }
    @Test fun oversizedExchangeDoesNotLeaveMisleadingOldContext() {
        val m=ConversationMemory(6,10);m.remember("a","b")
        m.remember("long message","response")
        assertTrue(m.snapshot().isEmpty())
    }
}
