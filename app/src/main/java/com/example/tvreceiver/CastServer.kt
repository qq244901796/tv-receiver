package com.example.tvreceiver

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class CastServer(
    private val onUrlReceived: (String) -> Unit
) : NanoHTTPD(PORT) {

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/health" -> {
                newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
            }

            session.method == Method.POST && session.uri == "/cast" -> {
                val files = HashMap<String, String>()
                return try {
                    session.parseBody(files)
                    val rawBody = files["postData"].orEmpty()
                    val json = JSONObject(rawBody)
                    val url = json.optString("url", "")
                    if (url.isBlank()) {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "missing url")
                    } else {
                        onUrlReceived(url)
                        newFixedLengthResponse(Response.Status.OK, "text/plain", "received")
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "error")
                }
            }

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }

    companion object {
        const val PORT = 9527
    }
}
