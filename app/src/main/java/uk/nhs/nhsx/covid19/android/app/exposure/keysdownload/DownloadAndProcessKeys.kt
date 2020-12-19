package uk.nhs.nhsx.covid19.android.app.exposure.keysdownload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import uk.nhs.nhsx.covid19.android.app.common.Result
import uk.nhs.nhsx.covid19.android.app.common.runSafely
import uk.nhs.nhsx.covid19.android.app.exposure.ExposureNotificationApi
import uk.nhs.nhsx.covid19.android.app.exposure.keysdownload.DownloadKeysParams.Intervals
import uk.nhs.nhsx.covid19.android.app.exposure.keysdownload.DownloadKeysParams.Intervals.Daily
import uk.nhs.nhsx.covid19.android.app.exposure.keysdownload.DownloadKeysParams.Intervals.Hourly
import uk.nhs.nhsx.covid19.android.app.remote.KeysDistributionApi
import uk.nhs.nhsx.covid19.android.app.util.hoursUntilNow
import java.io.File
import java.time.Clock
import javax.inject.Inject

class DownloadAndProcessKeys @Inject constructor(
    private val keysDistributionApi: KeysDistributionApi,
    private val exposureApi: ExposureNotificationApi,
    private val keyFilesCache: KeyFilesCache,
    private val downloadKeysParams: DownloadKeysParams,
    private val lastDownloadedKeyTimeProvider: LastDownloadedKeyTimeProvider,
    private val clock: Clock
) {

    suspend operator fun invoke(): Result<Unit> = withContext(Dispatchers.IO) {
        runSafely {
            if (exposureApi.isEnabled()) {
                Timber.d("Last downloaded keys time=${lastDownloadedKeyTimeProvider.getLatestStoredTime()}")

                val latestStoredTime = lastDownloadedKeyTimeProvider.getLatestStoredTime()
                if (latestStoredTime != null && latestStoredTime.hoursUntilNow(clock) < 4) {
                    Timber.d("Skipping because last downloaded keys less than 4 hours")
                    return@runSafely
                }

                Timber.d("Downloading keys")

                val keyFiles = mutableListOf<File>()
                var lastDownloadedInterval: Intervals? = null

                runCatching {
                    val intervals = downloadKeysParams.getNextQueries()
                    Timber.d("Key archive intervals to get: $intervals")
                    intervals.forEach {
                        keyFiles.add(getFile(it))
                        lastDownloadedInterval = it
                    }
                }.getOrElse {
                    Timber.d(it, "Exception while downloading key archive")
                }

                lastDownloadedInterval?.let {
                    processKeyFiles(keyFiles)
                    lastDownloadedKeyTimeProvider.saveLastStoredTime(it.timestamp)
                    Timber.d("Last downloaded key time updated to ${it.timestamp}")
                } ?: Timber.d("Last downloaded interval is null, not processing any key files")

                keyFilesCache.clearOutdatedFiles()
            }
        }
    }

    private suspend fun getFile(interval: Intervals): File =
        when (val file = keyFilesCache.getFile(interval.timestamp)) {
            null -> {
                Timber.d("$interval is not cached, downloading")
                when (interval) {
                    is Hourly -> keyFilesCache.createFile(
                        interval.timestamp,
                        keysDistributionApi.fetchHourlyKeys(interval.timestamp.zipExt())
                    )
                    is Daily -> keyFilesCache.createFile(
                        interval.timestamp,
                        keysDistributionApi.fetchDailyKeys(interval.timestamp.zipExt())
                    )
                }
            }
            else -> {
                Timber.d("$interval is cached")
                file
            }
        }

    private suspend fun processKeyFiles(keyFiles: List<File>) {
        Timber.d("Exposure API version=${exposureApi.version()}")
        Timber.d("Number of diagnosis keys provided=${keyFiles.size}")
        Timber.d("Key file locations: $keyFiles")
        exposureApi.provideDiagnosisKeys(keyFiles)
    }

    private fun String.zipExt() = "$this.zip"
}
