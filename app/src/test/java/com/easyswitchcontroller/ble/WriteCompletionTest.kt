package com.easyswitchcontroller.ble

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteCompletionTest {
    @Test
    fun `응답 없는 명령도 명시적인 전송 실패는 성공으로 바꾸지 않는다`() = runTest {
        val done = CompletableDeferred<Unit>()
        done.completeExceptionally(IOException("write rejected"))
        val error = runCatching { awaitWriteCompletion(done, false, 500) }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun `응답 없는 명령의 콜백 부재는 허용한다`() = runTest {
        assertFalse(awaitWriteCompletion(CompletableDeferred(), false, 500))
    }

    @Test
    fun `외부 취소는 성공으로 바뀌지 않는다`() = runTest {
        var returned = false
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitWriteCompletion(CompletableDeferred(), false, 500)
            returned = true
        }
        operation.cancel()
        operation.join()
        assertFalse(returned)
    }
}
