package uk.nhs.nhsx.covid19.android.app.remote.data

import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class VirologyTestResultResponse(
    val testEndDate: Instant,
    val testResult: VirologyTestResult
)

enum class VirologyTestResult {
    POSITIVE,
    NEGATIVE,
    VOID,
}
