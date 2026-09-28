package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch

class ModelRuntimeTest {
    @Test fun importingAndLoadingAreMutuallyExclusive() {
        val owner=Any()
        assertTrue(ModelRuntime.beginImport())
        try { assertFalse(ModelRuntime.beginLoad(owner));assertFalse(ModelRuntime.beginImport()) }
        finally { ModelRuntime.endImport() }
        assertTrue(ModelRuntime.beginLoad(owner))
        try { assertFalse(ModelRuntime.beginImport());assertFalse(ModelRuntime.beginLoad(Any())) }
        finally { ModelRuntime.release(owner) }
    }
    @Test fun previousOwnerCannotOverwriteNewLoad() {
        val old=Any();val new=Any()
        assertTrue(ModelRuntime.beginLoad(old));ModelRuntime.release(old)
        assertTrue(ModelRuntime.beginLoad(new))
        try {
            ModelRuntime.update(new,ModelState("ready","使用中","GGUF CPU"))
            ModelRuntime.release(old,"old failure")
            assertEquals("GGUF CPU",ModelRuntime.state.backend);assertFalse(ModelRuntime.available())
        } finally { ModelRuntime.release(new) }
    }
    @Test fun releasingBlocksReplacementUntilNativeFreeCompletes() {
        val owner=Any();assertTrue(ModelRuntime.beginLoad(owner))
        try {
            ModelRuntime.update(owner,ModelState("releasing","解放中"))
            assertFalse(ModelRuntime.beginLoad(Any()));assertFalse(ModelRuntime.beginImport())
        } finally { ModelRuntime.release(owner) }
        assertTrue(ModelRuntime.available());assertEquals("unloaded",ModelRuntime.state.phase)
    }
    @Test fun failureAllowsRetryWithoutClaimingReady() {
        val owner=Any();assertTrue(ModelRuntime.beginLoad(owner));ModelRuntime.release(owner,"Vulkan unavailable")
        assertEquals("failed",ModelRuntime.state.phase);assertTrue(ModelRuntime.available())
        assertTrue(ModelRuntime.beginLoad(owner));ModelRuntime.release(owner)
    }
    @Test fun simultaneousLoadImportHasOnlyOneWinner() {
        val owner=Any();val start=CountDownLatch(1);val results=BooleanArray(2)
        val a=Thread{start.await();results[0]=ModelRuntime.beginLoad(owner)}
        val b=Thread{start.await();results[1]=ModelRuntime.beginImport()}
        a.start();b.start();start.countDown();a.join();b.join()
        try { assertEquals(1,results.count{it}) } finally {ModelRuntime.release(owner);ModelRuntime.endImport()}
    }
    @Test fun unknownFileSizeDoesNotBecomeFakePercent() {
        assertNull(ImportProgress.percent(1024,null));assertNull(ImportProgress.percent(1024,0))
        assertEquals(50,ImportProgress.percent(1024,2048));assertEquals(100,ImportProgress.percent(2048,2048))
    }
}
