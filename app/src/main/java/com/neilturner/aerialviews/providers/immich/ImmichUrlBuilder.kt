package com.neilturner.aerialviews.providers.immich

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.enums.ImmichImageType
import com.neilturner.aerialviews.models.enums.ImmichVideoType
import com.neilturner.aerialviews.models.prefs.ImmichUrlPrefs
import java.net.URLEncoder

class ImmichUrlBuilder(
    private val server: String,
    private val prefs: ImmichUrlPrefs,
    private var resolvedSharedKey: String? = null,
    /** Set to true once the connected server is confirmed to be Immich v3+. */
    private var isV3: Boolean = false,
    /**
     * When set, API key URLs carry the key as an `apiKey` query parameter. Needed when the
     * URL is fetched by another app (eg. Projectivy) that cannot send the `X-API-Key` header.
     */
    private val urlApiKey: String? = null,
    private val uriFactory: (String) -> Uri = { it.toUri() },
) {
    fun setResolvedSharedKey(key: String?) {
        resolvedSharedKey = key
    }

    /** Mark this builder as targeting an Immich v3+ server. */
    fun setServerV3(v3: Boolean) {
        isV3 = v3
    }

    fun getAssetUri(
        id: String,
        isVideo: Boolean,
    ): Uri {
        val cleanedKey = resolvedSharedKey ?: cleanSharedLinkKey(prefs.pathName)
        val url =
            when (prefs.authType) {
                ImmichAuthType.SHARED_LINK -> {
                    val base =
                        if (isVideo) {
                            if (prefs.videoType == ImmichVideoType.TRANSCODED) {
                                "$server/api/assets/$id/video/playback?key=$cleanedKey"
                            } else {
                                "$server/api/assets/$id/original?key=$cleanedKey"
                            }
                        } else {
                            if (prefs.imageType == ImmichImageType.ORIGINAL) {
                                "$server/api/assets/$id/original?key=$cleanedKey"
                            } else {
                                val size = if (prefs.imageType == ImmichImageType.FULLSIZE) "fullsize" else "preview"
                                "$server/api/assets/$id/thumbnail?size=$size&key=$cleanedKey"
                            }
                        }
                    // v3: password query param is no longer accepted; skip it
                    if (!isV3 && prefs.password.isNotEmpty()) "$base&password=${prefs.password}" else base
                }

                // "fullsize" will use fullsize or reencoded pic as configured within Immich
                // "preview" will use preview-reencoded pic as configured within Immich, 1440p by default
                ImmichAuthType.API_KEY -> {
                    val base =
                        if (isVideo) {
                            if (prefs.videoType == ImmichVideoType.TRANSCODED) {
                                "$server/api/assets/$id/video/playback"
                            } else {
                                "$server/api/assets/$id/original"
                            }
                        } else {
                            if (prefs.imageType == ImmichImageType.ORIGINAL) {
                                "$server/api/assets/$id/original"
                            } else {
                                val size = if (prefs.imageType == ImmichImageType.FULLSIZE) "fullsize" else "preview"
                                "$server/api/assets/$id/thumbnail?size=$size"
                            }
                        }
                    withApiKey(base)
                }

                null -> {
                    throw IllegalStateException("Invalid authentication type")
                }
            }
        return uriFactory(url)
    }

    private fun withApiKey(url: String): String {
        val key = urlApiKey?.trim()?.takeIf { it.isNotEmpty() } ?: return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}apiKey=${URLEncoder.encode(key, "UTF-8")}"
    }
}
