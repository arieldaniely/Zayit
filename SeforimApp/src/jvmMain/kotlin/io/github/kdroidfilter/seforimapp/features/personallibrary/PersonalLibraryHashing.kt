package io.github.kdroidfilter.seforimapp.features.personallibrary

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal operator fun Int?.plus(value: Int): Int = (this ?: 0) + value
