package com.revanced.net.revancedmanager.core.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for providing localized strings
 */
interface StringProvider {
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String
    /** A `<plurals>` resource with [quantity] as its first format argument. */
    fun getQuantityString(resId: Int, quantity: Int): String
}

/**
 * Implementation of StringProvider that uses Android Context
 */
@Singleton
class StringProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StringProvider {
    
    override fun getString(resId: Int): String {
        return context.getString(resId)
    }
    
    override fun getString(resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }

    override fun getQuantityString(resId: Int, quantity: Int): String {
        return context.resources.getQuantityString(resId, quantity, quantity)
    }
} 