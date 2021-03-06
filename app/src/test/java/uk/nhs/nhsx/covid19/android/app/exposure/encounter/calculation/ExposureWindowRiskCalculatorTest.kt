package uk.nhs.nhsx.covid19.android.app.exposure.encounter.calculation

import com.google.android.gms.nearby.exposurenotification.ExposureWindow
import com.google.android.gms.nearby.exposurenotification.ExposureWindow.Builder
import com.google.android.gms.nearby.exposurenotification.Infectiousness
import com.google.android.gms.nearby.exposurenotification.ReportType
import com.jeroenmols.featureflag.framework.FeatureFlag
import com.jeroenmols.featureflag.framework.FeatureFlagTestHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import uk.nhs.nhsx.covid19.android.app.exposure.encounter.SubmitEpidemiologyData
import uk.nhs.nhsx.covid19.android.app.exposure.encounter.SubmitEpidemiologyData.ExposureWindowWithRisk
import uk.nhs.nhsx.covid19.android.app.remote.data.DurationDays
import uk.nhs.nhsx.covid19.android.app.remote.data.EpidemiologyEventType.EXPOSURE_WINDOW
import uk.nhs.nhsx.covid19.android.app.remote.data.V2RiskCalculation
import uk.nhs.nhsx.covid19.android.app.state.IsolationConfigurationProvider
import uk.nhs.riskscore.RiskScoreCalculator
import uk.nhs.riskscore.RiskScoreCalculatorConfiguration
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.google.android.gms.nearby.exposurenotification.ScanInstance as GoogleScanInstance
import uk.nhs.riskscore.ScanInstance as NHSScanInstance

class ExposureWindowRiskCalculatorTest {
    private val isolationConfigurationProvider = mockk<IsolationConfigurationProvider>()

    private val baseDate = Instant.parse("2020-07-20T00:00:00Z")
    private val clock = Clock.fixed(baseDate, ZoneOffset.UTC)
    private val riskScoreCalculatorProvider = mockk<RiskScoreCalculatorProvider>()
    private val epidemiologyEventProvider = mockk<EpidemiologyEventProvider>(relaxUnitFun = true)
    private val submitEpidemiologyData = mockk<SubmitEpidemiologyData>(relaxUnitFun = true)

    private val riskScoreCalculator = mockk<RiskScoreCalculator>()
    private val testScope = TestCoroutineScope()

    private val someRiskScoreCalculatorConfig = mockk<RiskScoreCalculatorConfiguration>()
    private val someRiskCalculation = V2RiskCalculation(
        daysSinceOnsetToInfectiousness = listOf(),
        infectiousnessWeights = listOf(1.0, 1.0, 1.0),
        reportTypeWhenMissing = 0,
        riskThreshold = 0.0
    )
    private val someScanInstance = getGoogleScanInstance(50)
    private val expectedRiskScore = 100.0

    private val riskCalculator = ExposureWindowRiskCalculator(
        clock,
        isolationConfigurationProvider,
        riskScoreCalculatorProvider,
        submitEpidemiologyData,
        epidemiologyEventProvider
    )

    @Before
    fun setup() {
        every { riskScoreCalculatorProvider.riskScoreCalculator(any()) } returns riskScoreCalculator
        every { riskScoreCalculatorProvider.getRiskCalculationVersion() } returns 2
        every { riskScoreCalculator.calculate(any()) } returns expectedRiskScore
        every { isolationConfigurationProvider.durationDays } returns DurationDays()
    }

    @After
    fun tearDown() {
        FeatureFlagTestHelper.clearFeatureFlags()
    }

    @Test
    fun `calls risk score calculator with mapped scan instances for each exposure window with exposure windows feature flag enabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.enableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val expectedAttenuationValue = 55
            val expectedSecondsSinceLastScan = 180
            val scanInstances = listOf(
                getGoogleScanInstance(expectedAttenuationValue, expectedSecondsSinceLastScan)
            )
            val exposureWindows = listOf(getExposureWindow(scanInstances))

            riskCalculator(exposureWindows, someRiskCalculation, someRiskScoreCalculatorConfig)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            DayRisk(
                                exposureWindows[0].dateMillisSinceEpoch,
                                expectedRiskScore * 60,
                                2
                            ),
                            exposureWindows[0]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }

            val expectedInstances = listOf(
                NHSScanInstance(expectedAttenuationValue, expectedSecondsSinceLastScan)
            )
            verify { riskScoreCalculator.calculate(expectedInstances) }
            verify(exactly = 1) { epidemiologyEventProvider.add(any()) }
        }

    @Test
    fun `calls risk score calculator with mapped scan instances for each exposure window with exposure windows feature flag disabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.disableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val expectedAttenuationValue = 55
            val expectedSecondsSinceLastScan = 180
            val scanInstances = listOf(
                getGoogleScanInstance(expectedAttenuationValue, expectedSecondsSinceLastScan)
            )
            val exposureWindows = listOf(getExposureWindow(scanInstances))

            riskCalculator(exposureWindows, someRiskCalculation, someRiskScoreCalculatorConfig)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            DayRisk(
                                exposureWindows[0].dateMillisSinceEpoch,
                                expectedRiskScore * 60,
                                2
                            ),
                            exposureWindows[0]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }

            val expectedInstances = listOf(
                NHSScanInstance(expectedAttenuationValue, expectedSecondsSinceLastScan)
            )
            verify { riskScoreCalculator.calculate(expectedInstances) }
            verify(exactly = 0) { epidemiologyEventProvider.add(any()) }
        }

    @Test
    fun `returns null if no risk score exceeds threshold`() = testScope.runBlockingTest {
        val riskCalculation = someRiskCalculation.copy(riskThreshold = 900.0)
        val exposureWindows = listOf(getExposureWindow(listOf()))
        every { riskScoreCalculator.calculate(any()) } returns 2.0

        val risk = riskCalculator(exposureWindows, riskCalculation, someRiskScoreCalculatorConfig)

        coVerify { submitEpidemiologyData.invoke(listOf(), epidemiologyEventType = EXPOSURE_WINDOW) }
        verify(exactly = 0) { epidemiologyEventProvider.add(any()) }

        assertNull(risk)
    }

    @Test
    fun `returns day risk when risk score exceeds threshold with exposure windows feature flag enabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.enableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val riskCalculation = someRiskCalculation.copy(riskThreshold = 50.0)
            val exposureWindows = listOf(getExposureWindow(scanInstances = listOf()))

            val risk =
                riskCalculator(exposureWindows, riskCalculation, someRiskScoreCalculatorConfig)

            val expectedRisk = DayRisk(baseDate.toEpochMilli(), expectedRiskScore * 60, 2)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            expectedRisk,
                            exposureWindows[0]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }
            verify(exactly = 1) { epidemiologyEventProvider.add(any()) }

            assertEquals(expectedRisk, risk)
        }

    @Test
    fun `returns day risk when risk score exceeds threshold with exposure windows feature flag disabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.disableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val riskCalculation = someRiskCalculation.copy(riskThreshold = 50.0)
            val exposureWindows = listOf(getExposureWindow(scanInstances = listOf()))

            val risk =
                riskCalculator(exposureWindows, riskCalculation, someRiskScoreCalculatorConfig)

            val expectedRisk = DayRisk(baseDate.toEpochMilli(), expectedRiskScore * 60, 2)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            expectedRisk,
                            exposureWindows[0]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }
            verify(exactly = 0) { epidemiologyEventProvider.add(any()) }

            assertEquals(expectedRisk, risk)
        }

    @Test
    fun `returns day risk when submitting epidemiology data throws exception with exposure windows feature flag enabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.enableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            coEvery { submitEpidemiologyData.invoke(any(), any()) } throws RuntimeException()

            val riskCalculation = someRiskCalculation.copy(riskThreshold = 50.0)
            val exposureWindows = listOf(getExposureWindow(scanInstances = listOf()))

            val risk =
                riskCalculator(exposureWindows, riskCalculation, someRiskScoreCalculatorConfig)

            val expectedRisk = DayRisk(baseDate.toEpochMilli(), expectedRiskScore * 60, 2)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            expectedRisk,
                            exposureWindows[0]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }
            verify(exactly = 1) { epidemiologyEventProvider.add(any()) }

            assertEquals(expectedRisk, risk)
        }

    @Test
    fun `returns day risk when submitting epidemiology data throws exception with exposure windows feature flag disabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.disableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            coEvery { submitEpidemiologyData.invoke(any(), any()) } throws RuntimeException()

            val riskCalculation = someRiskCalculation.copy(riskThreshold = 50.0)
            val exposureWindows = listOf(getExposureWindow(scanInstances = listOf()))

            val risk =
                riskCalculator(exposureWindows, riskCalculation, someRiskScoreCalculatorConfig)

            val expectedRisk = DayRisk(baseDate.toEpochMilli(), expectedRiskScore * 60, 2)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            expectedRisk,
                            exposureWindows[0]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }
            verify(exactly = 0) { epidemiologyEventProvider.add(any()) }

            assertEquals(expectedRisk, risk)
        }

    @Test
    fun `returns risk for most recent day which exceeds threshold with exposure windows feature flag enabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.enableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val olderDate = todayMinusDays(3)
            val newerDate = todayMinusDays(2)
            val exposureWindows = listOf(
                getExposureWindow(
                    millisSinceEpoch = olderDate.toStartOfDayEpochMillis()
                ),
                getExposureWindow(
                    millisSinceEpoch = newerDate.toStartOfDayEpochMillis()
                )
            )

            val risk =
                riskCalculator(exposureWindows, someRiskCalculation, someRiskScoreCalculatorConfig)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            DayRisk(olderDate.toStartOfDayEpochMillis(), expectedRiskScore * 60, 2),
                            exposureWindows[0]
                        ).toEpidemiologyEvent(),
                        ExposureWindowWithRisk(
                            DayRisk(newerDate.toStartOfDayEpochMillis(), expectedRiskScore * 60, 2),
                            exposureWindows[1]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }

            val slot = slot<List<EpidemiologyEvent>>()
            verify(exactly = 1) { epidemiologyEventProvider.add(capture(slot)) }
            assertEquals(2, slot.captured.size)

            val expectedDateMillis = newerDate.toStartOfDayEpochMillis()
            val expectedRisk =
                DayRisk(
                    expectedDateMillis,
                    expectedRiskScore * 60,
                    2
                )
            assertEquals(expectedRisk, risk)
        }

    @Test
    fun `returns risk for most recent day which exceeds threshold with exposure windows feature flag disabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.disableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val olderDate = todayMinusDays(3)
            val newerDate = todayMinusDays(2)
            val exposureWindows = listOf(
                getExposureWindow(
                    millisSinceEpoch = olderDate.toStartOfDayEpochMillis()
                ),
                getExposureWindow(
                    millisSinceEpoch = newerDate.toStartOfDayEpochMillis()
                )
            )

            val risk =
                riskCalculator(exposureWindows, someRiskCalculation, someRiskScoreCalculatorConfig)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            DayRisk(olderDate.toStartOfDayEpochMillis(), expectedRiskScore * 60, 2),
                            exposureWindows[0]
                        ).toEpidemiologyEvent(),
                        ExposureWindowWithRisk(
                            DayRisk(newerDate.toStartOfDayEpochMillis(), expectedRiskScore * 60, 2),
                            exposureWindows[1]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }

            verify(exactly = 0) { epidemiologyEventProvider.add(any()) }

            val expectedDateMillis = newerDate.toStartOfDayEpochMillis()
            val expectedRisk =
                DayRisk(
                    expectedDateMillis,
                    expectedRiskScore * 60,
                    2
                )
            assertEquals(expectedRisk, risk)
        }

    private fun todayMinusDays(days: Long) =
        baseDate.atZone(ZoneOffset.UTC).minusDays(days).toLocalDate()

    @Test
    fun `returns risk item with highest score when there are multiple from the same day with exposure windows feature flag enabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.enableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val millisSinceEpoch = baseDate.toEpochMilli()
            val higherRiskScanInstance = getGoogleScanInstance(30)
            val lowerRiskScanInstance = getGoogleScanInstance(50)
            val exposureWindows = listOf(
                getExposureWindow(listOf(lowerRiskScanInstance), millisSinceEpoch),
                getExposureWindow(listOf(higherRiskScanInstance), millisSinceEpoch)
            )
            val higherRiskScore = 200.0
            val lowerRiskScore = 100.0
            higherRiskScanInstance.returnsRiskScoreOf(higherRiskScore)
            lowerRiskScanInstance.returnsRiskScoreOf(lowerRiskScore)

            val risk =
                riskCalculator(exposureWindows, someRiskCalculation, someRiskScoreCalculatorConfig)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            DayRisk(millisSinceEpoch, lowerRiskScore * 60, 2), exposureWindows[0]
                        ).toEpidemiologyEvent(),
                        ExposureWindowWithRisk(
                            DayRisk(millisSinceEpoch, higherRiskScore * 60, 2), exposureWindows[1]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }

            val slot = slot<List<EpidemiologyEvent>>()
            verify(exactly = 1) { epidemiologyEventProvider.add(capture(slot)) }
            assertEquals(2, slot.captured.size)

            val expectedRisk =
                DayRisk(
                    millisSinceEpoch,
                    higherRiskScore * 60,
                    2
                )
            assertEquals(expectedRisk, risk)
        }

    @Test
    fun `returns risk item with highest score when there are multiple from the same day with exposure windows feature flag disabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.disableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val millisSinceEpoch = baseDate.toEpochMilli()
            val higherRiskScanInstance = getGoogleScanInstance(30)
            val lowerRiskScanInstance = getGoogleScanInstance(50)
            val exposureWindows = listOf(
                getExposureWindow(listOf(lowerRiskScanInstance), millisSinceEpoch),
                getExposureWindow(listOf(higherRiskScanInstance), millisSinceEpoch)
            )
            val higherRiskScore = 200.0
            val lowerRiskScore = 100.0
            higherRiskScanInstance.returnsRiskScoreOf(higherRiskScore)
            lowerRiskScanInstance.returnsRiskScoreOf(lowerRiskScore)

            val risk =
                riskCalculator(exposureWindows, someRiskCalculation, someRiskScoreCalculatorConfig)

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            DayRisk(millisSinceEpoch, lowerRiskScore * 60, 2), exposureWindows[0]
                        ).toEpidemiologyEvent(),
                        ExposureWindowWithRisk(
                            DayRisk(millisSinceEpoch, higherRiskScore * 60, 2), exposureWindows[1]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }

            verify(exactly = 0) { epidemiologyEventProvider.add(any()) }

            val expectedRisk =
                DayRisk(
                    millisSinceEpoch,
                    higherRiskScore * 60,
                    2
                )
            assertEquals(expectedRisk, risk)
        }

    @Test
    fun `multiplies calculated risk score by infectiousness factor with exposure windows feature flag enabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.enableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val expectedInfectiousness = Infectiousness.STANDARD
            val exposureWindows = listOf(
                getExposureWindow(listOf(someScanInstance), infectiousness = expectedInfectiousness)
            )
            val expectedInfectiousnessFactor = 0.4
            val riskCalculation = someRiskCalculation.copy(
                infectiousnessWeights = listOf(0.0, expectedInfectiousnessFactor, 1.0)
            )

            val risk =
                riskCalculator(exposureWindows, riskCalculation, someRiskScoreCalculatorConfig)

            val expectedRiskScore = expectedInfectiousnessFactor * expectedRiskScore * 60

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            DayRisk(exposureWindows[0].dateMillisSinceEpoch, expectedRiskScore, 2),
                            exposureWindows[0]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }
            verify(exactly = 1) {
                epidemiologyEventProvider.add(
                    listOf(
                        ExposureWindowWithRisk(
                            DayRisk(exposureWindows[0].dateMillisSinceEpoch, expectedRiskScore, 2),
                            exposureWindows[0]
                        ).toEpidemiologyEvent()
                    )
                )
            }

            assertEquals(expectedRiskScore, risk?.calculatedRisk)
        }

    @Test
    fun `multiplies calculated risk score by infectiousness factor with exposure windows feature flag disabled`() =
        testScope.runBlockingTest {
            FeatureFlagTestHelper.disableFeatureFlag(FeatureFlag.STORE_EXPOSURE_WINDOWS)

            val expectedInfectiousness = Infectiousness.STANDARD
            val exposureWindows = listOf(
                getExposureWindow(listOf(someScanInstance), infectiousness = expectedInfectiousness)
            )
            val expectedInfectiousnessFactor = 0.4
            val riskCalculation = someRiskCalculation.copy(
                infectiousnessWeights = listOf(0.0, expectedInfectiousnessFactor, 1.0)
            )

            val risk =
                riskCalculator(exposureWindows, riskCalculation, someRiskScoreCalculatorConfig)

            val expectedRiskScore = expectedInfectiousnessFactor * expectedRiskScore * 60

            coVerify {
                submitEpidemiologyData.invoke(
                    listOf(
                        ExposureWindowWithRisk(
                            DayRisk(exposureWindows[0].dateMillisSinceEpoch, expectedRiskScore, 2),
                            exposureWindows[0]
                        ).toEpidemiologyEvent()
                    ),
                    epidemiologyEventType = EXPOSURE_WINDOW
                )
            }
            verify(exactly = 0) {
                epidemiologyEventProvider.add(any())
            }

            assertEquals(expectedRiskScore, risk?.calculatedRisk)
        }

    @Test
    fun `disregards exposure from longer than one isolation period ago`() =
        testScope.runBlockingTest {
            val oldDate = baseDate.atZone(ZoneOffset.UTC).toLocalDate().minusDays(11)
            val exposureWindows = listOf(
                getExposureWindow(millisSinceEpoch = oldDate.toStartOfDayEpochMillis())
            )
            every { isolationConfigurationProvider.durationDays } returns DurationDays(contactCase = 10)

            val risk =
                riskCalculator(exposureWindows, someRiskCalculation, someRiskScoreCalculatorConfig)

            coVerify { submitEpidemiologyData.invoke(listOf(), epidemiologyEventType = EXPOSURE_WINDOW) }
            verify(exactly = 0) { epidemiologyEventProvider.add(any()) }

            assertNull(risk)
        }

    private fun GoogleScanInstance.returnsRiskScoreOf(riskScore: Double) {
        val scanInstances = listOf(NHSScanInstance(minAttenuationDb, secondsSinceLastScan))
        every { riskScoreCalculator.calculate(scanInstances) } returns riskScore
    }

    private fun getExposureWindow(
        scanInstances: List<com.google.android.gms.nearby.exposurenotification.ScanInstance> = listOf(
            someScanInstance
        ),
        millisSinceEpoch: Long = baseDate.toEpochMilli(),
        infectiousness: Int = Infectiousness.HIGH
    ): ExposureWindow {
        return Builder().apply {
            setDateMillisSinceEpoch(millisSinceEpoch)
            setReportType(ReportType.CONFIRMED_TEST)
            setScanInstances(scanInstances)
            setInfectiousness(infectiousness)
        }.build()
    }

    private fun getGoogleScanInstance(
        minAttenuation: Int,
        secondsSinceLastScan: Int = 180,
        typicalAttenuation: Int = 68
    ) = GoogleScanInstance.Builder().apply {
        setMinAttenuationDb(minAttenuation)
        setSecondsSinceLastScan(secondsSinceLastScan)
        setTypicalAttenuationDb(typicalAttenuation)
    }.build()

    private fun LocalDate.toStartOfDayEpochMillis(): Long =
        atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}
