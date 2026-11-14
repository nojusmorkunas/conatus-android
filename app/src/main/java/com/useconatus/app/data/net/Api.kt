package com.useconatus.app.data.net

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownServiceException

// The parts of the Conatus API the app uses. Shapes come from the server's route handlers in
// app/api and the zod schemas in lib/validation.ts; unknown fields are ignored so a newer
// server doesn't break an older app.

interface ConatusApi {
    @GET("api/health")
    suspend fun health(): HealthDto

    @POST("api/v1/auth/device-token")
    suspend fun deviceToken(@Body body: PasswordTokenRequest): DeviceTokenDto

    @POST("api/v1/auth/device-token")
    suspend fun deviceToken(@Body body: CodeTokenRequest): DeviceTokenDto

    /** [authorization] is only for checking a brand-new token before it's stored. */
    /** Revokes the token it's sent with. Servers before 1.3.3 don't have it. */
    @DELETE("api/v1/auth/device-token")
    suspend fun revokeDeviceToken(@Header("Authorization") authorization: String): Response<Unit>

    @GET("api/v1/context")
    suspend fun context(@Header("Authorization") authorization: String? = null): ContextDto

    @GET("api/v1/tasks")
    suspend fun tasks(
        @Query("updatedSince") updatedSince: String?,
        @Query("cursor") cursor: String?,
        @Query("includeDeleted") includeDeleted: Boolean = true,
        @Query("limit") limit: Int = 100,
    ): TaskPageDto

    @GET("api/v1/tasks/{id}")
    suspend fun task(@Path("id") id: String): TaskDto

    // Writes take the body as raw JSON so a retry sends exactly the same bytes.
    @POST("api/v1/tasks")
    suspend fun createTask(@Header("Idempotency-Key") idempotencyKey: String, @Body body: RequestBody): TaskDto

    @PATCH("api/v1/tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body body: RequestBody): ResponseBody

    @DELETE("api/v1/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): ResponseBody

    @POST("api/v1/trash/tasks/{id}/restore")
    suspend fun restoreTask(@Path("id") id: String): ResponseBody

    @POST("api/v1/trash/projects/{id}/restore")
    suspend fun restoreProject(@Path("id") id: String): ResponseBody

    @GET("api/v1/projects")
    suspend fun projects(): List<ProjectDto>

    @GET("api/v1/sections")
    suspend fun sections(): List<SectionDto>

    @GET("api/v1/labels")
    suspend fun labels(): List<LabelDto>

    // Projects, sections and labels: create returns the new item; the rest only need to succeed.
    @POST("api/v1/projects")
    suspend fun createProject(@Header("Idempotency-Key") idempotencyKey: String?, @Body body: RequestBody): ProjectDto

    @PATCH("api/v1/projects/{id}")
    suspend fun updateProject(@Path("id") id: String, @Body body: RequestBody): ResponseBody

    @DELETE("api/v1/projects/{id}")
    suspend fun deleteProject(@Path("id") id: String): ResponseBody

    @POST("api/v1/sections")
    suspend fun createSection(@Header("Idempotency-Key") idempotencyKey: String?, @Body body: RequestBody): SectionDto

    @PATCH("api/v1/sections/{id}")
    suspend fun updateSection(@Path("id") id: String, @Body body: RequestBody): ResponseBody

    @DELETE("api/v1/sections/{id}")
    suspend fun deleteSection(@Path("id") id: String): ResponseBody

    @POST("api/v1/labels")
    suspend fun createLabel(@Header("Idempotency-Key") idempotencyKey: String?, @Body body: RequestBody): LabelDto

    @PATCH("api/v1/labels/{id}")
    suspend fun updateLabel(@Path("id") id: String, @Body body: RequestBody): ResponseBody

    @DELETE("api/v1/labels/{id}")
    suspend fun deleteLabel(@Path("id") id: String): ResponseBody

    // Comments and reminders can only be listed per task (or project, for comments).
    @GET("api/v1/comments")
    suspend fun comments(@Query("taskId") taskId: String? = null, @Query("projectId") projectId: String? = null): List<CommentDto>

    @POST("api/v1/comments")
    suspend fun createComment(@Body body: RequestBody): CommentDto

    @PATCH("api/v1/comments/{id}")
    suspend fun updateComment(@Path("id") id: String, @Body body: RequestBody): ResponseBody

    @DELETE("api/v1/comments/{id}")
    suspend fun deleteComment(@Path("id") id: String): ResponseBody

    @GET("api/v1/reminders")
    suspend fun reminders(@Query("taskId") taskId: String): List<ReminderDto>

    /** Reminders whose time has come and that nobody has seen yet, across every task. */
    @GET("api/v1/reminders?due=1")
    suspend fun dueReminders(): List<ReminderDto>

    /** Every reminder nobody has seen yet, including ones still to come. Older servers answer 400. */
    @GET("api/v1/reminders?pending=1")
    suspend fun pendingReminders(): List<ReminderDto>

    @POST("api/v1/reminders")
    suspend fun createReminder(@Body body: RequestBody): ReminderDto

    /** Takes {"seen": true}, or {"remindAt": …} to move it (a snooze). Older servers only take the first. */
    @PATCH("api/v1/reminders/{id}")
    suspend fun updateReminder(@Path("id") id: String, @Body body: RequestBody): ResponseBody

    @DELETE("api/v1/reminders/{id}")
    suspend fun deleteReminder(@Path("id") id: String): ResponseBody

    @PATCH("api/v1/settings")
    suspend fun updateSettings(@Body body: RequestBody): ResponseBody

    /** Where the server posts a wake-up when something changes elsewhere, one per sign-in. Older servers answer 404. */
    @PUT("api/v1/push/subscription")
    suspend fun setPushAddress(@Body body: PushAddressDto): Response<Unit>

    @DELETE("api/v1/push/subscription")
    suspend fun deletePushAddress(): Response<Unit>

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun create(baseUrl: HttpUrl, client: OkHttpClient): ConatusApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ConatusApi::class.java)
    }
}

@Serializable data class HealthDto(val status: String)

@Serializable data class PasswordTokenRequest(val username: String, val password: String, val deviceName: String)

@Serializable data class CodeTokenRequest(val code: String, val codeVerifier: String)

@Serializable data class DeviceTokenDto(val id: String, val token: String)

@Serializable data class ContextDto(val today: String, val user: UserDto)

/** [weekStart] is 0 for Sunday to 6 for Saturday. [dateFormat] is yyyy-MM-dd, MM/dd/yyyy, dd/MM/yyyy or dd.MM.yyyy. */
@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val name: String? = null,
    val timezone: String,
    val weekStart: Int? = null,
    val dateFormat: String? = null,
    /** Whether the server emails reminders as well. */
    val emailReminders: Boolean? = null,
    /** False when the server can't email reminders: no SMTP, or no address. Older servers don't say. */
    val emailRemindersAvailable: Boolean = true,
)

@Serializable
data class CommentDto(
    val id: String,
    val taskId: String? = null,
    val projectId: String? = null,
    val userId: String? = null,
    val content: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable data class ReminderDto(val id: String, val taskId: String, val remindAt: String)

@Serializable data class PushAddressDto(val url: String)

@Serializable data class TaskPageDto(val items: List<TaskDto>, val nextCursor: String? = null, val serverTime: String)

@Serializable
data class TaskDto(
    val id: String,
    val projectId: String,
    val sectionId: String? = null,
    val parentId: String? = null,
    val content: String,
    val description: String? = null,
    val priority: Int = 4,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val deadlineDate: String? = null,
    val recurrence: String? = null,
    val durationMinutes: Int? = null,
    val isCompleted: Boolean = false,
    val completedAt: String? = null,
    val deletedAt: String? = null,
    val recurrenceEndDate: String? = null,
    val order: String = "",
    val updatedAt: String,
    val labels: List<LabelRefDto> = emptyList(),
    val commentCount: Int = 0,
)

@Serializable data class LabelRefDto(val id: String)

@Serializable
data class ProjectDto(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val color: String = "gray",
    val isFavorite: Boolean = false,
    val isInbox: Boolean = false,
    val order: String = "",
    val shared: Boolean = false,
    val isArchived: Boolean = false,
    val icon: String? = null,
)

@Serializable
data class SectionDto(val id: String, val projectId: String, val name: String, val order: String = "", val isArchived: Boolean = false)

@Serializable
data class LabelDto(val id: String, val name: String, val color: String = "gray", val isFavorite: Boolean = false, val order: String = "")

/** Turns what a user types as a server address into a base URL, or null if it can't be one. */
fun normalizeServerUrl(input: String): HttpUrl? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (url.host.isEmpty() || url.querySize > 0 || url.fragment != null) return null
    return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build()
}

/** What went wrong talking to the server, worded for the person holding the phone. */
sealed class ApiProblem(val message: String) {
    class Offline(message: String) : ApiProblem(message)
    class Unauthorized(message: String) : ApiProblem(message)
    class Server(val status: Int, message: String) : ApiProblem(message)
}

fun Throwable.toApiProblem(): ApiProblem = when (this) {
    is HttpException -> {
        val serverMessage = response()?.errorBody()?.string()?.let(::errorMessageFrom)
        when (code()) {
            401 -> ApiProblem.Unauthorized(serverMessage ?: "The server didn't accept this sign-in.")
            429 -> {
                val wait = response()?.headers()?.get("Retry-After")?.toLongOrNull()
                ApiProblem.Server(429, if (wait != null) "Too many attempts. Try again in ${(wait + 59) / 60} minutes." else "Too many attempts. Try again later.")
            }
            else -> ApiProblem.Server(code(), serverMessage ?: "The server answered with error ${code()}.")
        }
    }
    is UnknownServiceException -> ApiProblem.Offline("This server needs HTTPS. Plain http only works for a server on your own network.")
    is IOException -> {
        // The message stays general, so the cause goes to logcat.
        Log.w("Conatus", "Couldn't reach the server", this)
        ApiProblem.Offline("Couldn't reach the server. Check the address and your connection.")
    }
    else -> ApiProblem.Server(0, "Something unexpected went wrong: ${this::class.simpleName}.")
}

/** The server replies `{ "error": "..." }`, or field errors as an object for invalid input. */
internal fun errorMessageFrom(body: String): String? = runCatching {
    when (val error = ConatusApi.json.parseToJsonElement(body).jsonObject["error"]) {
        is JsonPrimitive -> error.content
        // Field errors: each field with its messages. An empty object (the server lost the reason) reads as none.
        is JsonObject -> error.entries.joinToString("; ") { (field, messages) ->
            "$field: ${(messages as? JsonArray)?.joinToString(", ") { it.jsonPrimitive.content } ?: messages}"
        }.ifEmpty { null }
        else -> null
    }
}.getOrNull()

/**
 * Whether connecting to [address] is local network access, which Android 17 only allows apps
 * targeting it with ACCESS_LOCAL_NETWORK: private IPv4 ranges, link-local, and IPv6 unique local.
 */
fun isLocalNetwork(address: InetAddress): Boolean =
    address.isSiteLocalAddress || address.isLinkLocalAddress ||
        (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)

/**
 * Whether plain http may go to [address]: this phone or the local network, where the token and
 * tasks don't cross the internet unencrypted. Everything else needs HTTPS.
 */
fun allowsPlainHttp(address: InetAddress): Boolean = address.isLoopbackAddress || isLocalNetwork(address)

/** Whether the server is on the local network. Resolves its name, so call it off the main thread. */
fun isOnLocalNetwork(url: HttpUrl): Boolean =
    url.host.endsWith(".local") || runCatching { InetAddress.getAllByName(url.host).any(::isLocalNetwork) }.getOrDefault(false)
