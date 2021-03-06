package uk.nhs.nhsx.covid19.android.app.testordering

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import uk.nhs.nhsx.covid19.android.app.remote.EmptyApi
import uk.nhs.nhsx.covid19.android.app.remote.data.EmptySubmissionRequest
import uk.nhs.nhsx.covid19.android.app.remote.data.EmptySubmissionSource.KEY_SUBMISSION

class SubmitFakeKeysTest {

    private val emptyApi = mockk<EmptyApi>(relaxed = true)
    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope()

    private val testSubject = SubmitFakeKeys(emptyApi, testScope, testDispatcher)

    @Test
    fun `when invoking fake key submission then empty api is called`() = testScope.runBlockingTest {
        testSubject.invoke()

        coVerify { emptyApi.submit(EmptySubmissionRequest(KEY_SUBMISSION)) }
    }
}
