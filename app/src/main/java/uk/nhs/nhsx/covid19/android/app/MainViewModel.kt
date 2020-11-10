package uk.nhs.nhsx.covid19.android.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import uk.nhs.nhsx.covid19.android.app.exposure.ExposureNotificationApi
import uk.nhs.nhsx.covid19.android.app.onboarding.OnboardingCompletedProvider
import uk.nhs.nhsx.covid19.android.app.onboarding.PolicyUpdateProvider
import uk.nhs.nhsx.covid19.android.app.util.viewutils.DeviceDetection
import uk.nhs.nhsx.covid19.android.app.util.defaultFalse
import javax.inject.Inject

class MainViewModel @Inject constructor(
    private val deviceDetection: DeviceDetection,
    private val exposureNotificationApi: ExposureNotificationApi,
    private val onboardingCompletedProvider: OnboardingCompletedProvider,
    private val policyUpdateProvider: PolicyUpdateProvider
) : ViewModel() {

    private val mainViewStateLiveData = MutableLiveData<MainViewState>()

    fun viewState(): LiveData<MainViewState> = mainViewStateLiveData

    fun start() {
        viewModelScope.launch {

            val state: MainViewState = when {
                deviceDetection.isTablet() -> MainViewState.TabletNotSupported
                !exposureNotificationApi.isAvailable() -> MainViewState.ExposureNotificationsNotAvailable
                onboardingCompletedProvider.value.defaultFalse() && !policyUpdateProvider.isPolicyAccepted() -> MainViewState.PolicyUpdated
                onboardingCompletedProvider.value.defaultFalse() && policyUpdateProvider.isPolicyAccepted() -> MainViewState.PolicyAccepted
                else -> MainViewState.OnboardingStarted
            }
            mainViewStateLiveData.postValue(state)
        }
    }

    sealed class MainViewState {

        object TabletNotSupported : MainViewState()

        object ExposureNotificationsNotAvailable : MainViewState()

        object OnboardingStarted : MainViewState()

        object PolicyUpdated : MainViewState()

        object PolicyAccepted : MainViewState()
    }
}
