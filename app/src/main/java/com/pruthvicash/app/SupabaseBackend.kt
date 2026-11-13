package com.pruthvicash.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Loan(val id: String, val userId: String, val amount: Int, val months: Int, val purpose: String, val status: String)

/** Uses only the Supabase public anon/publishable key; database access is constrained by RLS. */
class SupabaseBackend {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private var accessToken: String? = null
    private var userId: String? = null
    private val configured: Boolean get() = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private fun request(path: String, method: String = "GET", body: String? = null, prefer: String? = null): String {
        if (!configured) throw IOException("Supabase is not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties or Gradle properties.")
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + path
        val builder = Request.Builder().url(url).header("apikey", BuildConfig.SUPABASE_ANON_KEY)
        accessToken?.let { builder.header("Authorization", "Bearer $it") } ?: builder.header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        if (body != null) builder.header("Content-Type", "application/json").method(method, body.toRequestBody(jsonType)) else builder.method(method, null)
        prefer?.let { builder.header("Prefer", it) }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try { JSONObject(text).optString("msg", JSONObject(text).optString("message", text)) } catch (_: Exception) { text }
                throw IOException(detail.ifBlank { "Request failed (${response.code})." })
            }
            return text
        }
    }

    suspend fun register(email: String, password: String) = withContext(Dispatchers.IO) {
        request("/auth/v1/signup", "POST", JSONObject().put("email", email).put("password", password).toString())
    }

    suspend fun login(email: String, password: String): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val result = JSONObject(request("/auth/v1/token?grant_type=password", "POST", JSONObject().put("email", email).put("password", password).toString()))
        accessToken = result.getString("access_token")
        val user = result.getJSONObject("user")
        userId = user.getString("id")
        val profile = request("/rest/v1/profiles?id=eq.${userId}&select=role")
        val rows = JSONArray(profile)
        val isAdmin = rows.length() > 0 && rows.getJSONObject(0).optString("role") == "admin"
        email to isAdmin
    }

    suspend fun fetchLoans(): List<Loan> = withContext(Dispatchers.IO) {
        val result = JSONArray(request("/rest/v1/loan_applications?select=id,user_id,amount,tenure_months,purpose,status&order=created_at.desc"))
        (0 until result.length()).map { i ->
            val row = result.getJSONObject(i)
            Loan(row.getString("id"), row.getString("user_id"), row.getInt("amount"), row.getInt("tenure_months"), row.optString("purpose"), row.optString("status", "pending"))
        }
    }

    suspend fun submitLoan(amount: Int, months: Int, purpose: String) = withContext(Dispatchers.IO) {
        request("/rest/v1/loan_applications", "POST", JSONObject().put("amount", amount).put("tenure_months", months).put("purpose", purpose).toString(), "return=minimal")
    }

    suspend fun updateStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        request("/rest/v1/loan_applications?id=eq.$id", "PATCH", JSONObject().put("status", status).toString(), "return=minimal")
    }

    fun signOut() { accessToken = null; userId = null }
}
