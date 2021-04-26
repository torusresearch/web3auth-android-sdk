package com.openlogin.core

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log

class OpenLogin(
    private val context: Activity,
    private val clientId: String,
    private val network: Network,
    redirectUrl: String,
    iframeUrl: String? = null,
) {
    enum class Network {
        MAINNET, TESTNET, DEVELOPMENT
    }

    companion object {
        object Method {
            const val LOGIN = "openlogin_login"
            const val LOGOUT = "openlogin_logout"
        }

        init {
            installBouncyCastle()
        }
    }

    private val iframeUrl: Uri
    private val redirectUrl: Uri

    private var _privKey: String? = null
    val privKey: String?
        get() = _privKey

    private var _store: Map<*, *>? = null
    val store: Map<*, *>?
        get() = _store

    init {
        when {
            network === Network.MAINNET -> {
                this.iframeUrl = Uri.parse("https://app.openlogin.com");
            }
            network === Network.TESTNET -> {
                this.iframeUrl = Uri.parse("https://beta.openlogin.com");
            }
            iframeUrl != null -> {
                this.iframeUrl = Uri.parse(iframeUrl)
            }
            else -> throw Exception("Unspecified network and iframeUrl");
        }

        this.redirectUrl = Uri.parse(redirectUrl)

        // Get result in query and hash params
        val resultUrl = context.intent.data
        val resultData = mutableMapOf<String, Any>()

        if (resultUrl != null) {
            try {
                for (attr in resultUrl.queryParameterNames) {
                    if (attr == "result") continue
                    resultData[attr] = resultUrl.getQueryParameters(attr)
                }

                val resultQueryParams = resultUrl.getQueryParameter("result")
                if (resultQueryParams != null) {
                    val json =
                        gson.fromJson<Map<String, Any>>(
                            Base64.decode(resultQueryParams, Base64.URL_SAFE)
                                .toString(Charsets.UTF_8),
                            Map::class.java
                        )
                    for (entry in json) resultData[entry.key] = entry.value
                }

                val hashUrl = Uri.Builder().scheme(resultUrl.scheme)
                    .encodedAuthority(resultUrl.encodedAuthority)
                    .encodedPath(resultUrl.encodedPath)
                    .encodedQuery(resultUrl.encodedFragment)
                    .build()
                val resultHashParams = hashUrl.getQueryParameter("result")
                if (resultHashParams != null) {
                    val json =
                        gson.fromJson<Map<String, Any>>(
                            Base64.decode(resultHashParams, Base64.URL_SAFE)
                                .toString(Charsets.UTF_8),
                            Map::class.java
                        )
                    for (entry in json) resultData[entry.key] = entry.value
                }
            } catch (e: Throwable) {
                Log.e("${javaClass.name}#init", e.message, e)
            }
        }

        val resultPrivKey = resultData["privKey"]
        if (resultPrivKey is String) _privKey = resultPrivKey

        val resultStore = resultData["store"]
        if (resultStore is Map<*, *>) _store = resultStore
    }

    private fun request(method: String, params: Map<String, Any>) {
        val pid = randomPid()

        val origin = Uri.Builder().scheme(redirectUrl.scheme)
            .encodedAuthority(redirectUrl.encodedAuthority)
            .toString()

        val mergedParams = mutableMapOf(
            "redirectUrl" to redirectUrl.toString(),
            "_clientId" to clientId,
            "_origin" to origin,
            "_originData" to emptyMap<String, Nothing>()
        )
        mergedParams.putAll(params)

        /* Add current session
        val currPrivKey = privKey
        if (currPrivKey != null) {
            val userData = mapOf(
                "clientId" to clientId,
                "timestamp" to System.currentTimeMillis().toString()
            )

            mergedParams["_userData"] = userData
        }
        */

        val hash = Uri.Builder().scheme(iframeUrl.scheme)
            .encodedAuthority(iframeUrl.encodedAuthority)
            .encodedPath(iframeUrl.encodedPath)
            .appendPath("start")
            .appendQueryParameter("_pid", pid)
            .appendQueryParameter("_method", Method.LOGIN)
            .appendQueryParameter(
                "b64Params",
                Base64.encodeToString(
                    gson.toJson(mergedParams).toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE
                )
            )
            .build().encodedQuery ?: ""

        val url = Uri.Builder().scheme(iframeUrl.scheme)
            .encodedAuthority(iframeUrl.encodedAuthority)
            .encodedPath(iframeUrl.encodedPath)
            .appendPath("start")
            .encodedFragment(hash)
            .build()

        context.startActivity(Intent(Intent.ACTION_VIEW, url))
    }

    fun login(loginProvider: String) {
        request(Method.LOGIN, mapOf("loginProvider" to loginProvider))
    }

    fun logout() {
        request(Method.LOGOUT, emptyMap())
        this._privKey = null
    }
}

