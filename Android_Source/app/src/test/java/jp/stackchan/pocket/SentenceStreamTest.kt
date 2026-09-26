package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class SentenceStreamTest {
    @Test fun sentenceArrivesBeforeGenerationFinishes() { val s=SentenceStream();s.append("こんにちは");assertNull(s.next());s.append("。次の");assertEquals("こんにちは。",s.next());assertFalse(s.complete());s.append("文です。");assertEquals("次の文です。",s.next());s.finish();assertTrue(s.complete()) }
    @Test fun unfinishedTailIsSpokenOnlyOnCompletion() { val s=SentenceStream();s.append("句点なし");assertNull(s.next());s.finish();assertEquals("句点なし",s.next());assertTrue(s.complete()) }
    @Test fun multipleSentencesKeepOrderAndFullText() { val s=SentenceStream();s.append("はい！本当？最後");s.finish();assertEquals("はい！",s.next());assertEquals("本当？",s.next());assertEquals("最後",s.next());assertEquals("はい！本当？最後",s.text()) }
    @Test(expected=IllegalStateException::class) fun failureDoesNotSpeakQueuedText() { val s=SentenceStream();s.append("未再生。");s.fail(Exception());s.next() }
    @Test(expected=IllegalStateException::class) fun outputIsBounded() { val s=SentenceStream(3);s.append("1234");s.next() }
    @Test fun lateCallbackDoesNotChangeCompletedOutput() { val s=SentenceStream();s.append("完了");s.finish();s.append("余分");assertEquals("完了",s.next()) }
    @Test fun earlyClausePreservesTextAndOnlySplitsFirstClause() {
        val s=SentenceStream(earlyClause=true);s.append("まずは最初のお話を、次の内容ですが、まだ続きます")
        assertEquals("まずは最初のお話を、",s.next());assertNull(s.next());s.finish()
        assertEquals("次の内容ですが、まだ続きます",s.next());assertTrue(s.complete())
        assertEquals("まずは最初のお話を、次の内容ですが、まだ続きます",s.text())
    }
    @Test fun briefAcknowledgementWaitsForSentence() {
        val s=SentenceStream(earlyClause=true);s.append("はい、了解しました");assertNull(s.next());s.append("。");assertEquals("はい、了解しました。",s.next())
    }
    @Test fun earlyModeStillHonorsEarlierFullStop() {
        val s=SentenceStream(earlyClause=true);s.append("はい。これは次の文で、続きです。");assertEquals("はい。",s.next());assertEquals("これは次の文で、続きです。",s.next())
    }
    @Test fun laterCommaAfterShortAcknowledgementStartsSpeech() {
        val s=SentenceStream(earlyClause=true)
        s.append("はい、まず今日の予定を");assertNull(s.next())
        s.append("確認して、詳しく説明します。")
        assertEquals("はい、まず今日の予定を確認して、",s.next())
        assertEquals("詳しく説明します。",s.next());s.finish();assertTrue(s.complete())
        assertEquals("はい、まず今日の予定を確認して、詳しく説明します。",s.text())
    }
    @Test fun disabledEarlyModeWaitsPastLaterComma() {
        val s=SentenceStream();s.append("はい、まず今日の予定を確認して、")
        assertNull(s.next());s.finish();assertEquals("はい、まず今日の予定を確認して、",s.next())
    }
    @Test fun shorterMeaningfulClauseCanStartSpeech() {
        val s=SentenceStream(earlyClause=true);s.append("おすすめは、散歩です");assertEquals("おすすめは、",s.next());assertNull(s.next());s.finish();assertEquals("散歩です",s.next())
    }
    @Test fun fillerAloneDoesNotStartSpeech() {
        val s=SentenceStream(earlyClause=true);s.append("そうですね、");assertNull(s.next());s.append("散歩がいいですよ。");assertEquals("そうですね、散歩がいいですよ。",s.next())
    }
    @Test fun quotedCommasAreNotEarlyBoundaries() {
        val s=SentenceStream(earlyClause=true);s.append("「こんにちは、世界」と言い、説明します");assertEquals("「こんにちは、世界」と言い、",s.next());s.finish();assertEquals("説明します",s.next())
    }
    @Test fun noPunctuationMeansNoArbitrarySplit() {
        val s=SentenceStream(earlyClause=true);val text="これは句読点なしで途中に区切りがない文章です";s.append(text);assertNull(s.next());s.finish();assertEquals(text,s.next())
    }
}
