package com.useconatus.app.data.sync

import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.data.db.TaskEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

// The three-way merge behind silent sync. Each queued change remembers the values it replaced
// (its base). When the server's version arrives, a field the server left at the base takes the
// phone's value; a field both sides changed to different values is a conflict for the user.

/** A task field by its name in the API, as JSON. Labels compare as a set. */
fun TaskEntity.field(name: String): JsonElement = when (name) {
    "content" -> JsonPrimitive(content)
    "description" -> JsonPrimitive(description)
    "priority" -> JsonPrimitive(priority)
    "dueDate" -> JsonPrimitive(dueDate)
    "dueTime" -> JsonPrimitive(dueTime)
    "deadlineDate" -> JsonPrimitive(deadlineDate)
    "durationMinutes" -> JsonPrimitive(durationMinutes)
    "recurrence" -> JsonPrimitive(recurrence)
    "labelIds" -> JsonArray(labelIds.sorted().map(::JsonPrimitive))
    "completed" -> JsonPrimitive(isCompleted)
    "projectId" -> JsonPrimitive(projectId)
    "sectionId" -> JsonPrimitive(sectionId)
    "parentId" -> JsonPrimitive(parentId)
    else -> throw IllegalArgumentException("Unknown task field $name")
}

fun TaskEntity.withField(name: String, value: JsonElement): TaskEntity {
    val primitive = value as? JsonPrimitive
    val text = primitive?.takeIf { it !is JsonNull }?.content
    return when (name) {
        "content" -> copy(content = text ?: content)
        "description" -> copy(description = text.orEmpty())
        "priority" -> copy(priority = primitive?.intOrNull ?: priority)
        "dueDate" -> copy(dueDate = text)
        "dueTime" -> copy(dueTime = text)
        "deadlineDate" -> copy(deadlineDate = text)
        "durationMinutes" -> copy(durationMinutes = primitive?.intOrNull)
        "recurrence" -> copy(recurrence = text)
        "labelIds" -> copy(labelIds = (value as JsonArray).map { it.jsonPrimitive.content })
        "completed" -> (primitive?.booleanOrNull ?: isCompleted).let { done ->
            copy(isCompleted = done, completedAt = if (done) completedAt ?: Instant.now().toString() else null)
        }
        "projectId" -> copy(projectId = text ?: projectId)
        "sectionId" -> copy(sectionId = text)
        "parentId" -> copy(parentId = text)
        else -> this
    }
}

/** The current values of [fields], as the base of a change about to be made. */
fun TaskEntity.baseFor(fields: Collection<String>) = JsonObject(fields.associateWith { field(it) })

fun TaskEntity.withFields(values: JsonObject) = values.entries.fold(this) { task, (name, value) -> task.withField(name, value) }

fun jsonObjectOf(text: String?): JsonObject = text?.let { Json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())

/** The server's version with the phone's queued changes on top, and what both sides changed differently. */
data class Merged(val task: TaskEntity, val conflicts: JsonObject)

fun merge(local: TaskEntity, theirs: TaskEntity, queued: List<PendingChange>): Merged {
    // A field changed twice on the phone is compared against what it was before the first change.
    val bases = linkedMapOf<String, JsonElement>()
    for (change in queued.sortedBy { it.seq }) jsonObjectOf(change.base).forEach { (name, value) -> bases.putIfAbsent(name, value) }
    val conflicts = mutableMapOf<String, JsonElement>()
    var task = theirs
    for ((name, base) in bases) {
        val ours = local.field(name)
        val other = theirs.field(name)
        if (other != base && other != ours) conflicts[name] = other
        // The phone's value stays on screen, conflicting or not, until the user decides.
        task = task.withField(name, ours)
    }
    return Merged(task, JsonObject(conflicts))
}
