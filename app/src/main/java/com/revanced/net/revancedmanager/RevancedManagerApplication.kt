package com.revanced.net.revancedmanager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.revanced.net.revancedmanager.core.common.LocaleHelper
import com.revanced.net.revancedmanager.data.local.preferences.PreferencesManager
import com.revanced.net.revancedmanager.data.manager.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * Main application class for ReVanced Manager
 * Enables Hilt dependency injection throughout the app
 * Now includes Activity tracking for reliable language switching
 */
@HiltAndroidApp
class RevancedManagerApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    companion object {
        @Volatile
        private var currentActivity: WeakReference<Activity>? = null
        
        /**
         * Get current activity for language switching
         */
        fun getCurrentActivity(): Activity? {
            return currentActivity?.get()
        }
        
        /**
         * Set current activity
         */
        internal fun setCurrentActivity(activity: Activity?) {
            currentActivity = if (activity != null) {
                WeakReference(activity)
            } else {
                null
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Register activity lifecycle callbacks for tracking
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                setCurrentActivity(activity)
                android.util.Log.i("RevancedApp", "🏃 Activity created: ${activity::class.simpleName}@${System.identityHashCode(activity)}")
                android.util.Log.d("RevancedApp", "🏃 Current activity set to: ${getCurrentActivity()?.let { "${it::class.simpleName}@${System.identityHashCode(it)}" }}")
            }
            
            override fun onActivityResumed(activity: Activity) {
                setCurrentActivity(activity)
                android.util.Log.i("RevancedApp", "🏃 Activity resumed: ${activity::class.simpleName}@${System.identityHashCode(activity)}")
                android.util.Log.d("RevancedApp", "🏃 Current activity set to: ${getCurrentActivity()?.let { "${it::class.simpleName}@${System.identityHashCode(it)}" }}")
            }
            
            override fun onActivityPaused(activity: Activity) {
                android.util.Log.d("RevancedApp", "🏃 Activity paused: ${activity::class.simpleName}@${System.identityHashCode(activity)}")
                // Keep reference during pause
            }
            
            override fun onActivityDestroyed(activity: Activity) {
                android.util.Log.i("RevancedApp", "🏃 Activity destroyed: ${activity::class.simpleName}@${System.identityHashCode(activity)}")
                if (getCurrentActivity() == activity) {
                    setCurrentActivity(null)
                    android.util.Log.d("RevancedApp", "🏃 Current activity cleared")
                } else {
                    android.util.Log.d("RevancedApp", "🏃 Destroyed activity was not current activity")
                }
            }
            
            override fun onActivityStarted(activity: Activity) {
                android.util.Log.d("RevancedApp", "🏃 Activity started: ${activity::class.simpleName}@${System.identityHashCode(activity)}")
            }
            
            override fun onActivityStopped(activity: Activity) {
                android.util.Log.d("RevancedApp", "🏃 Activity stopped: ${activity::class.simpleName}@${System.identityHashCode(activity)}")
            }
            
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                android.util.Log.d("RevancedApp", "🏃 Activity save instance state: ${activity::class.simpleName}@${System.identityHashCode(activity)}")
            }
        })
        
        // Apply saved language configuration using LocaleHelper
        val config = preferencesManager.getAppConfig()
        val languageCode = if (config.language.code.contains("-")) {
            config.language.code.split("-")[0]
        } else {
            config.language.code
        }
        
        // Set default locale
        val locale = java.util.Locale(languageCode)
        java.util.Locale.setDefault(locale)
        android.util.Log.d("RevancedApp", "Set default locale to: ${java.util.Locale.getDefault()}")

        // Daily background update check (the worker no-ops when disabled in settings)
        UpdateCheckWorker.schedule(WorkManager.getInstance(this))
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    override fun attachBaseContext(base: Context?) {
        // Apply language configuration using LocaleHelper for the base context
        base?.let { context ->
            val sharedPrefs = context.getSharedPreferences("ReVancedManagerPreferences", Context.MODE_PRIVATE)
            val languageCode = sharedPrefs.getString("language", null)
            
            if (languageCode != null) {
                // Handle language codes with country codes (e.g., es-ES -> es)
                val cleanLanguageCode = if (languageCode.contains("-")) {
                    languageCode.split("-")[0]
                } else {
                    languageCode
                }
                
                val contextWithLanguage = LocaleHelper.setLocale(context, cleanLanguageCode)
                super.attachBaseContext(contextWithLanguage)
                return
            }
        }
        
        super.attachBaseContext(base)
    }
}