package com.salat.camrec.presentation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.salat.coroutines.IoCoroutineScope
import com.salat.sharedevents.domain.repository.SharedEventsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class AutoLaunchAccessibilityService : AccessibilityService() {

    @Inject
    @IoCoroutineScope
    lateinit var serviceScope: CoroutineScope

    @Inject
    lateinit var sharedEvents: SharedEventsRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        configureAccessibilityService()
        serviceScope.launch { sharedEvents.setAccessibilityServiceEnabled(true) }
        Timber.d("[AS] Connected")
    }

    @Suppress("ReturnCount")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    private fun configureAccessibilityService() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onInterrupt() {
        Timber.d("[AS] Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch { sharedEvents.setAccessibilityServiceEnabled(false) }
        Timber.d("[AS] Destroyed")
    }
}
