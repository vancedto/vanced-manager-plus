package com.revanced.net.revancedmanager.core.common

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import com.revanced.net.revancedmanager.RevancedManagerApplication
import java.util.Locale

/**
 * Manual locale configuration helper
 * This approach bypasses AppCompatDelegate and directly configures context
 * More reliable for immediate language switching
 */
class LocaleHelper {
    companion object {
        private const val TAG = "LocaleHelper"
        
        /**
         * Set locale and return new context with updated configuration
         */
        fun setLocale(context: Context, language: String): Context {
            val locale = Locale(language)
            Locale.setDefault(locale)

            // Copy full configuration (density, screen size, night mode…) then override locale only
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)

            android.util.Log.d(TAG, "Setting locale to: $language")
            android.util.Log.d(TAG, "Default locale now: ${Locale.getDefault()}")

            return context.createConfigurationContext(config)
        }
        
        /**
         * Wrap context with new locale configuration
         */
        fun wrap(context: Context, language: String): ContextWrapper {
            val config = Configuration()
            val locale = Locale(language)
            config.setLocale(locale)
            
            val newContext = context.createConfigurationContext(config)
            android.util.Log.d(TAG, "Wrapped context with locale: $language")
            
            return ContextWrapper(newContext)
        }
        
        /**
         * Apply locale to activity with recreation
         */
        fun applyLocaleToActivity(context: Context, language: String) {
            android.util.Log.i(TAG, "🏠 === APPLYING LOCALE WITH ACTIVITY RECREATION ===")
            android.util.Log.i(TAG, "🏠 Target language: $language")
            android.util.Log.i(TAG, "🏠 Current context: ${context::class.simpleName}")
            android.util.Log.i(TAG, "🏠 Context toString: $context")
            android.util.Log.i(TAG, "🏠 Current thread: ${Thread.currentThread().name}")
            
            // Set default locale first
            val locale = Locale(language)
            val previousLocale = Locale.getDefault()
            android.util.Log.i(TAG, "🏠 Previous default locale: $previousLocale")
            
            Locale.setDefault(locale)
            val newDefaultLocale = Locale.getDefault()
            android.util.Log.i(TAG, "🏠 ✅ Set default locale to: $newDefaultLocale")
            android.util.Log.i(TAG, "🏠 Locale change successful: ${previousLocale != newDefaultLocale}")
            
            // Try to get activity from Application tracker (direct access)
            android.util.Log.d(TAG, "🏠 Attempting to get activity from Application tracker...")
            var activity = try {
                // Direct access to companion object method - much simpler!
                val result = RevancedManagerApplication.getCurrentActivity()
                android.util.Log.d(TAG, "🏠 Direct access result: $result")
                result
            } catch (e: Exception) {
                android.util.Log.w(TAG, "🏠 💥 Failed to get activity from Application tracker", e)
                android.util.Log.w(TAG, "🏠 💥 Exception type: ${e::class.simpleName}")
                android.util.Log.w(TAG, "🏠 💥 Exception message: ${e.message}")
                null
            }
            
            android.util.Log.i(TAG, "🏠 Activity from tracker: ${activity?.let { "${it::class.simpleName}@${System.identityHashCode(it)}" } ?: "null"}")
            
            // Fallback: try to find activity from context
            if (activity == null) {
                android.util.Log.d(TAG, "🏠 Tracker failed, trying context search...")
                activity = findActivityFromContext(context)
                android.util.Log.i(TAG, "🏠 Activity from context search: ${activity?.let { "${it::class.simpleName}@${System.identityHashCode(it)}" } ?: "null"}")
            }
            
            if (activity != null) {
                android.util.Log.i(TAG, "🏠 Found activity: ${activity::class.simpleName}")
                android.util.Log.i(TAG, "🏠 Activity hash: ${System.identityHashCode(activity)}")
                android.util.Log.i(TAG, "🏠 Activity isFinishing: ${activity.isFinishing}")
                android.util.Log.i(TAG, "🏠 Activity isDestroyed: ${activity.isDestroyed}")
                
                val isValidActivity = !activity.isFinishing && !activity.isDestroyed
                android.util.Log.i(TAG, "🏠 Activity is valid: $isValidActivity")
                
                if (isValidActivity) {
                    android.util.Log.i(TAG, "🏠 ✅ Found valid activity: ${activity::class.simpleName}")
                    android.util.Log.i(TAG, "🏠 🔄 Recreating activity for language change...")
                    
                    android.util.Log.d(TAG, "🏠 About to call runOnUiThread...")
                    activity.runOnUiThread {
                        android.util.Log.i(TAG, "🏠 📱 Inside runOnUiThread")
                        android.util.Log.i(TAG, "🏠 📱 UI Thread: ${Thread.currentThread().name}")
                        
                        try {
                            android.util.Log.i(TAG, "🏠 📱 🔄 Updating activity resources directly...")
                            
                            // Update activity's resources configuration
                            val resources = activity.resources
                            android.util.Log.d(TAG, "🏠 📱 Got activity resources: $resources")
                            
                            val config = Configuration(resources.configuration)
                            android.util.Log.d(TAG, "🏠 📱 Created configuration copy: $config")
                            android.util.Log.d(TAG, "🏠 📱 Current config locale: ${config.locales}")
                            
                            config.setLocale(locale)
                            android.util.Log.i(TAG, "🏠 📱 Set locale in configuration: ${config.locales}")
                            
                            // Force update resources
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                android.util.Log.d(TAG, "🏠 📱 Using modern createConfigurationContext method...")
                                val newContext = activity.createConfigurationContext(config)
                                android.util.Log.i(TAG, "🏠 📱 ✅ Created new configuration context: $newContext")
                            } else {
                                android.util.Log.d(TAG, "🏠 📱 Using legacy updateConfiguration method...")
                                @Suppress("DEPRECATION")
                                resources.updateConfiguration(config, resources.displayMetrics)
                                android.util.Log.i(TAG, "🏠 📱 ✅ Updated configuration (legacy method)")
                            }
                            
                            android.util.Log.i(TAG, "🏠 📱 🔄 Now calling activity.recreate()...")
                            android.util.Log.i(TAG, "🏠 📱 Activity before recreate: ${activity::class.simpleName}@${System.identityHashCode(activity)}")
                            
                            activity.recreate()
                            
                            android.util.Log.i(TAG, "🏠 📱 🔄 Activity recreation called successfully")
                            android.util.Log.i(TAG, "🏠 📱 Note: Activity should be destroyed and recreated now")
                            
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "🏠 📱 💥 Failed to recreate activity", e)
                            android.util.Log.e(TAG, "🏠 📱 💥 Exception type: ${e::class.simpleName}")
                            android.util.Log.e(TAG, "🏠 📱 💥 Exception message: ${e.message}")
                            android.util.Log.e(TAG, "🏠 📱 💥 Exception cause: ${e.cause}")
                        }
                    }
                    
                    android.util.Log.d(TAG, "🏠 runOnUiThread call completed")
                } else {
                    android.util.Log.w(TAG, "🏠 ❌ Activity is not valid for recreation")
                    android.util.Log.w(TAG, "🏠 Activity state - isFinishing: ${activity.isFinishing}, isDestroyed: ${activity.isDestroyed}")
                }
            } else {
                android.util.Log.w(TAG, "🏠 ❌ No activity found for recreation")
                android.util.Log.w(TAG, "🏠 Context search and tracker both failed")
            }
            
            android.util.Log.i(TAG, "🏠 === APPLY LOCALE TO ACTIVITY END ===")
        }
        
        /**
         * Find activity from context (fallback method)
         */
        private fun findActivityFromContext(context: Context): android.app.Activity? {
            var ctx = context
            var depth = 0
            while (ctx is android.content.ContextWrapper && depth < 10) {
                if (ctx is android.app.Activity) {
                    android.util.Log.d(TAG, "Found activity at depth $depth: ${ctx::class.simpleName}")
                    return ctx
                }
                ctx = ctx.baseContext
                depth++
            }
            android.util.Log.d(TAG, "No activity found in context chain after $depth levels")
            return null
        }
    }
} 