package io.github.kdroidfilter.seforimapp.framework.portable

import com.russhwolf.settings.Settings
import java.io.File
import java.util.Properties

class PortableSettings(
    private val file: File = File(PortablePaths.dataDir, SETTINGS_FILE_NAME),
) : Settings {
    private val lock = Any()
    private val properties = Properties()

    init {
        file.parentFile?.mkdirs()
        if (file.exists()) {
            file.inputStream().use(properties::load)
        }
    }

    override val keys: Set<String>
        get() = synchronized(lock) { properties.stringPropertyNames().toSet() }

    override val size: Int
        get() = synchronized(lock) { properties.size }

    override fun clear() =
        synchronized(lock) {
            properties.clear()
            saveLocked()
        }

    override fun remove(key: String) =
        synchronized(lock) {
            properties.remove(key)
            saveLocked()
        }

    override fun hasKey(key: String): Boolean = synchronized(lock) { properties.containsKey(key) }

    override fun putInt(
        key: String,
        value: Int,
    ) = putString(key, value.toString())

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = getStringOrNull(key)?.toIntOrNull() ?: defaultValue

    override fun getIntOrNull(key: String): Int? = getStringOrNull(key)?.toIntOrNull()

    override fun putLong(
        key: String,
        value: Long,
    ) = putString(key, value.toString())

    override fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = getStringOrNull(key)?.toLongOrNull() ?: defaultValue

    override fun getLongOrNull(key: String): Long? = getStringOrNull(key)?.toLongOrNull()

    override fun putString(
        key: String,
        value: String,
    ) = synchronized(lock) {
        properties.setProperty(key, value)
        saveLocked()
    }

    override fun getString(
        key: String,
        defaultValue: String,
    ): String = getStringOrNull(key) ?: defaultValue

    override fun getStringOrNull(key: String): String? = synchronized(lock) { properties.getProperty(key) }

    override fun putFloat(
        key: String,
        value: Float,
    ) = putString(key, value.toString())

    override fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = getStringOrNull(key)?.toFloatOrNull() ?: defaultValue

    override fun getFloatOrNull(key: String): Float? = getStringOrNull(key)?.toFloatOrNull()

    override fun putDouble(
        key: String,
        value: Double,
    ) = putString(key, value.toString())

    override fun getDouble(
        key: String,
        defaultValue: Double,
    ): Double = getStringOrNull(key)?.toDoubleOrNull() ?: defaultValue

    override fun getDoubleOrNull(key: String): Double? = getStringOrNull(key)?.toDoubleOrNull()

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) = putString(key, value.toString())

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = getStringOrNull(key)?.toBooleanStrictOrNull() ?: defaultValue

    override fun getBooleanOrNull(key: String): Boolean? = getStringOrNull(key)?.toBooleanStrictOrNull()

    private fun saveLocked() {
        file.parentFile?.mkdirs()
        file.outputStream().use { properties.store(it, "Zayit portable settings") }
    }

    private companion object {
        const val SETTINGS_FILE_NAME = "settings.properties"
    }
}
