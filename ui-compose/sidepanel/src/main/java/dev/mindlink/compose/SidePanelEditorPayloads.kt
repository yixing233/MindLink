// SPDX-License-Identifier: GPL-3.0-only
// Platform payload channel between the isolated Compose runtime and the Java host.
// Only mutable platform fields cross the bridge; no Kotlin collections or lambdas.
package dev.mindlink.compose

/** City code written by the weather settings page before invoking its host callback. */
object WeatherCityPayload {
    @JvmStatic
    var CODE: String = ""
}

/** Hitokoto settings written by the hitokoto settings page before invoking its host callback. */
object PendingHitokotoSettings {
    @JvmStatic
    fun get(): dev.mindlink.compose.HitokotoPayload = Holder.PAYLOAD

    private object Holder { val PAYLOAD = HitokotoPayload() }
}

class HitokotoPayload {
    @JvmField
    var categories: String = ""
    @JvmField
    var minLength: Int? = null
    @JvmField
    var maxLength: Int? = null
    @JvmField
    var showSource: Boolean = true
    @JvmField
    var showAuthor: Boolean = true
}
