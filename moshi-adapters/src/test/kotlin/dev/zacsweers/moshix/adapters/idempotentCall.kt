package dev.zacsweers.moshix.adapters

import okhttp3.*
import okio.Timeout

/**
 * Always returns the same success [response]
 */
internal fun IdempotentCallFactory(response: String) = object : Call.Factory {

  override fun newCall(request: Request): Call = object : Call {

    override fun execute() = Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(200).message("OK")
      .body(ResponseBody.create(MediaType.get("application/json"), response))
      .build()

    override fun clone() = newCall(request)
    override fun cancel() = Unit
    override fun timeout() = Timeout.NONE
    override fun request() = request
    override fun isExecuted() = true
    override fun isCanceled() = false
    override fun enqueue(callback: Callback) = callback.onResponse(this, execute())
  }
}
