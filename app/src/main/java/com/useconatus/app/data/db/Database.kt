package com.useconatus.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Room is the single source of truth: screens read only from here, and sync writes server data here.

/** Tasks the lists show: not in Trash, and not in an archived or deleted project. */
private const val VISIBLE = "deletedAt IS NULL AND projectId NOT IN (SELECT id FROM projects WHERE isArchived = 1 OR deletedAt IS NOT NULL)"

@Entity(tableName = "tasks", indices = [Index("projectId"), Index("dueDate"), Index("parentId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sectionId: String?,
    val parentId: String?,
    val content: String,
    val description: String,
    val priority: Int,
    /** "YYYY-MM-DD" in the user's time zone, as the server stores it. */
    val dueDate: String?,
    /** "HH:mm". */
    val dueTime: String?,
    val deadlineDate: String?,
    val recurrence: String?,
    val durationMinutes: Int?,
    val isCompleted: Boolean,
    val order: String,
    val updatedAt: String,
    val labelIds: List<String>,
    val commentCount: Int,
    /** An ISO instant. Set while the task is completed. */
    @ColumnInfo(defaultValue = "NULL") val completedAt: String? = null,
    /** An ISO instant. Set while the task is in Trash. */
    @ColumnInfo(defaultValue = "NULL") val deletedAt: String? = null,
    /** The last date a repeating task runs on, inclusive. */
    @ColumnInfo(defaultValue = "NULL") val recurrenceEndDate: String? = null,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val color: String,
    val isFavorite: Boolean,
    val isInbox: Boolean,
    val order: String,
    val shared: Boolean,
    /** Archived projects hide from every list; the server stops sending them and their tasks. */
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    /** An ISO instant. Set while the project is in Trash. */
    @ColumnInfo(defaultValue = "NULL") val deletedAt: String? = null,
    /** One of the web's icon names ("home", "work"); none or one the phone doesn't know shows a folder. */
    @ColumnInfo(defaultValue = "NULL") val icon: String? = null,
)

@Entity(tableName = "sections", indices = [Index("projectId")])
data class SectionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val order: String,
    val isArchived: Boolean = false,
)

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val isFavorite: Boolean,
    val order: String,
)

/**
 * A change made on the phone that the server hasn't confirmed yet, queued only while a server is
 * connected. Changes go out in [seq] order. [body] is the exact JSON sent, so a retry is
 * byte-identical (the server's idempotency check compares bodies). [base] holds the values the
 * change replaced, for the three-way merge that spots conflicts. A change the server rejected
 * keeps its [failure] until the user retries or discards it; later changes to the task wait.
 */
@Entity(tableName = "pending_changes", indices = [Index("taskId")])
data class PendingChange(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    /** The id of what changed (see [target]): a server id, or [LOCAL_ID_PREFIX] plus a UUID before the server has it. */
    val taskId: String,
    val kind: ChangeKind,
    val body: String,
    /** What the change is, in words, for the list of changes that couldn't sync. */
    val summary: String,
    val idempotencyKey: String? = null,
    val failure: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val base: String? = null,
    /** What [taskId] names: a task, or a project, section, label, comment or reminder. */
    @ColumnInfo(defaultValue = "Task") val target: Target = Target.Task,
)

enum class Target { Task, Project, Section, Label, Comment, Reminder }

/** [Place] moves a task among its siblings: {sectionId, parentId, afterId}, as drag and drop does on the web. */
enum class ChangeKind { Create, Update, Complete, Reopen, SetLabels, Delete, Restore, Place }

/**
 * A task changed on this phone and elsewhere since the last sync, in the same fields and to
 * different values. Its queued changes wait until the user keeps one side. [theirs] is the
 * server's values of those fields as JSON; [deleted] means the server deleted it instead.
 */
@Entity(tableName = "conflicts")
data class Conflict(@PrimaryKey val taskId: String, val theirs: String, val deleted: Boolean = false)

const val LOCAL_ID_PREFIX = "local:"

/** A comment on a task or on a project; exactly one of [taskId] and [projectId] is set, as on the server. */
@Entity(tableName = "comments", indices = [Index("taskId"), Index("projectId")])
data class CommentEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val projectId: String?,
    val content: String,
    /** ISO instants. */
    val createdAt: String,
    val updatedAt: String,
    /** Who wrote it; null for one written on this phone before it had a server. */
    val userId: String?,
)

/** A reminder for a task at [remindAt], in epoch milliseconds. [notified] is this phone's own: it has shown it. */
@Entity(tableName = "reminders", indices = [Index("taskId")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val remindAt: Long,
    val notified: Boolean = false,
)

data class SubtaskCount(val parentId: String, val total: Int, val done: Int)

data class ProjectCount(val projectId: String, val open: Int)

@Dao
interface TaskDao {
    @Upsert
    suspend fun upsert(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    /**
     * Drops tasks whose project has left the phone, because the user can no longer reach it, which
     * delta sync can't report. Archived and trashed projects keep theirs: the server doesn't send
     * them again when the project comes back.
     */
    @Query("DELETE FROM tasks WHERE projectId NOT IN (SELECT id FROM projects) AND id NOT LIKE 'local:%'")
    suspend fun deleteOrphans()

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDate IS NOT NULL AND dueDate <= :today AND $VISIBLE")
    fun observeDueBy(today: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDate >= :from AND $VISIBLE ORDER BY dueDate, dueTime IS NULL, dueTime, `order`")
    fun observeUpcoming(from: String): Flow<List<TaskEntity>>

    /** Everything open that's due by [until], which takes in repeating tasks that started before a calendar's range. */
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDate IS NOT NULL AND dueDate <= :until AND $VISIBLE ORDER BY dueDate, dueTime IS NULL, dueTime, `order`")
    fun observeScheduledBy(until: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE (content LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') AND $VISIBLE ORDER BY isCompleted, dueDate IS NULL, dueDate, `order` LIMIT 100")
    fun observeSearch(query: String): Flow<List<TaskEntity>>

    /** As on the web: the last 200 completions, without sub-tasks whose parent is completed too. */
    @Query(
        "SELECT * FROM tasks WHERE isCompleted = 1 AND $VISIBLE AND (parentId IS NULL OR parentId NOT IN (SELECT id FROM tasks WHERE isCompleted = 1))" +
            " ORDER BY completedAt IS NULL, completedAt DESC LIMIT 200",
    )
    fun observeCompleted(): Flow<List<TaskEntity>>

    /** Tasks in Trash, without those whose parent or project is in Trash too (they come back with it). */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NOT NULL AND projectId NOT IN (SELECT id FROM projects WHERE deletedAt IS NOT NULL)" +
            " AND (parentId IS NULL OR parentId NOT IN (SELECT id FROM tasks WHERE deletedAt IS NOT NULL)) ORDER BY deletedAt DESC",
    )
    fun observeTrash(): Flow<List<TaskEntity>>

    /** Tasks made on this phone before a server was connected: the server doesn't have them and nothing is queued. */
    @Query("SELECT * FROM tasks WHERE id LIKE 'local:%' AND deletedAt IS NULL AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun unsent(): List<TaskEntity>

    /** Everything made on this phone before a server was connected: tasks, projects (but the Inbox), sections and labels. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND id LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)) + (SELECT COUNT(*) FROM projects WHERE isInbox = 0 AND id LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes))" +
            " + (SELECT COUNT(*) FROM sections WHERE id LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)) + (SELECT COUNT(*) FROM labels WHERE id LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes))",
    )
    fun observeUnsentCount(): Flow<Int>

    @Query("UPDATE tasks SET projectId = :newId WHERE projectId = :oldId")
    suspend fun moveProject(oldId: String, newId: String)

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE projectId = :projectId")
    suspend fun inProject(projectId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND isCompleted = 0 AND deletedAt IS NULL")
    fun observeOpenInProject(projectId: String): Flow<List<TaskEntity>>

    /** Labels are stored comma-separated, and ids are UUIDs, so a substring match is exact enough. */
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND labelIds LIKE '%' || :labelId || '%' AND $VISIBLE")
    fun observeOpenWithLabel(labelId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentId = :parentId AND deletedAt IS NULL")
    suspend fun children(parentId: String): List<TaskEntity>

    /** Sub-tasks in Trash, which come back with their parent. */
    @Query("SELECT * FROM tasks WHERE parentId = :parentId AND deletedAt IS NOT NULL")
    suspend fun deletedChildren(parentId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE labelIds LIKE '%' || :labelId || '%'")
    suspend fun withLabel(labelId: String): List<TaskEntity>

    // Renaming an id everywhere, when the server creates something the phone made.
    @Query("UPDATE tasks SET id = :new WHERE id = :old")
    suspend fun renameId(old: String, new: String)

    @Query("UPDATE tasks SET sectionId = :new WHERE sectionId = :old")
    suspend fun renameSection(old: String, new: String)

    @Query("UPDATE tasks SET labelIds = REPLACE(labelIds, :old, :new)")
    suspend fun renameLabel(old: String, new: String)

    @Query("SELECT * FROM tasks WHERE parentId = :id AND deletedAt IS NULL")
    fun observeChildren(id: String): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET parentId = :serverId WHERE parentId = :localId")
    suspend fun renameParent(localId: String, serverId: String)

    @Query("SELECT parentId, COUNT(*) AS total, SUM(isCompleted) AS done FROM tasks WHERE parentId IS NOT NULL AND deletedAt IS NULL GROUP BY parentId")
    fun observeSubtaskCounts(): Flow<List<SubtaskCount>>

    @Query("SELECT projectId, COUNT(*) AS open FROM tasks WHERE isCompleted = 0 AND deletedAt IS NULL GROUP BY projectId")
    fun observeOpenCounts(): Flow<List<ProjectCount>>

    @Query("UPDATE tasks SET commentCount = MAX(0, commentCount + :by) WHERE id = :id")
    suspend fun countComment(id: String, by: Int)

    @Query("UPDATE tasks SET commentCount = :count WHERE id = :id")
    suspend fun setCommentCount(id: String, count: Int)
}

@Dao
interface CommentDao {
    @Upsert
    suspend fun upsert(comments: List<CommentEntity>)

    @Query("SELECT * FROM comments WHERE id = :id")
    suspend fun get(id: String): CommentEntity?

    @Query("SELECT * FROM comments WHERE taskId = :taskId ORDER BY createdAt")
    fun observeForTask(taskId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE projectId = :projectId ORDER BY createdAt")
    fun observeForProject(projectId: String): Flow<List<CommentEntity>>

    @Query("SELECT COUNT(*) FROM comments WHERE taskId = :taskId")
    suspend fun countForTask(taskId: String): Int

    @Query("DELETE FROM comments WHERE id = :id")
    suspend fun delete(id: String)

    /** Drops what the server no longer has for a task or project, keeping comments with changes still to send. */
    @Query("DELETE FROM comments WHERE (taskId = :scope OR projectId = :scope) AND id NOT IN (:keep) AND id NOT LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun deleteOthers(scope: String, keep: List<String>)

    @Query("UPDATE comments SET id = :new WHERE id = :old")
    suspend fun renameId(old: String, new: String)

    @Query("UPDATE comments SET taskId = :new WHERE taskId = :old")
    suspend fun renameTask(old: String, new: String)

    @Query("UPDATE comments SET projectId = :new WHERE projectId = :old")
    suspend fun renameProject(old: String, new: String)

    @Query("SELECT * FROM comments WHERE id LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun unsent(): List<CommentEntity>
}

@Dao
interface ReminderDao {
    @Upsert
    suspend fun upsert(reminders: List<ReminderEntity>)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE taskId = :taskId ORDER BY remindAt")
    fun observeForTask(taskId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE taskId = :taskId")
    suspend fun forTask(taskId: String): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM reminders WHERE taskId = :taskId AND id NOT IN (:keep) AND id NOT LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun deleteOthers(taskId: String, keep: List<String>)

    @Query("SELECT id FROM reminders")
    suspend fun ids(): List<String>

    /** The next reminder to show: not shown yet, for a task that's still open and not in Trash. */
    @Query("SELECT reminders.* FROM reminders JOIN tasks ON tasks.id = reminders.taskId WHERE notified = 0 AND tasks.isCompleted = 0 AND tasks.deletedAt IS NULL ORDER BY remindAt LIMIT 1")
    fun observeNext(): Flow<ReminderEntity?>

    @Query("SELECT reminders.* FROM reminders JOIN tasks ON tasks.id = reminders.taskId WHERE notified = 0 AND tasks.isCompleted = 0 AND tasks.deletedAt IS NULL AND remindAt <= :now ORDER BY remindAt")
    suspend fun due(now: Long): List<ReminderEntity>

    @Query("UPDATE reminders SET notified = 1 WHERE id = :id")
    suspend fun setNotified(id: String)

    @Query("UPDATE reminders SET id = :new WHERE id = :old")
    suspend fun renameId(old: String, new: String)

    @Query("UPDATE reminders SET taskId = :new WHERE taskId = :old")
    suspend fun renameTask(old: String, new: String)

    @Query("SELECT * FROM reminders WHERE id LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun unsent(): List<ReminderEntity>
}

@Dao
interface PendingChangeDao {
    @Insert
    suspend fun add(change: PendingChange): Long

    @Query("SELECT * FROM pending_changes ORDER BY seq")
    suspend fun all(): List<PendingChange>

    @Query("SELECT * FROM pending_changes ORDER BY seq")
    fun observeAll(): Flow<List<PendingChange>>

    @Query("SELECT * FROM pending_changes WHERE taskId = :taskId ORDER BY seq")
    suspend fun forTask(taskId: String): List<PendingChange>

    @Query("SELECT COUNT(*) FROM pending_changes")
    fun observeCount(): Flow<Int>

    @Update
    suspend fun update(change: PendingChange)

    /** Swaps an id inside every queued body, when the server gives a project or task its real id. */
    @Query("UPDATE pending_changes SET body = REPLACE(body, :old, :new)")
    suspend fun replaceInBodies(old: String, new: String)

    @Query("DELETE FROM pending_changes WHERE seq = :seq")
    suspend fun delete(seq: Long)

    @Query("DELETE FROM pending_changes WHERE taskId = :taskId AND seq >= :fromSeq")
    suspend fun deleteFrom(taskId: String, fromSeq: Long)

    @Query("UPDATE pending_changes SET failure = :failure WHERE seq = :seq")
    suspend fun setFailure(seq: Long, failure: String?)

    @Query("UPDATE pending_changes SET taskId = :serverId WHERE taskId = :localId")
    suspend fun renameTask(localId: String, serverId: String)
}

@Dao
interface SectionDao {
    @Upsert
    suspend fun upsert(sections: List<SectionEntity>)

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun get(id: String): SectionEntity?

    @Query("SELECT * FROM sections WHERE projectId = :projectId")
    suspend fun inProject(projectId: String): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE projectId = :projectId ORDER BY `order`")
    fun observeInProject(projectId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE isArchived = 0 ORDER BY `order`")
    fun observeOpen(): Flow<List<SectionEntity>>

    @Query("DELETE FROM sections WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    /** Sections made or changed on the phone stay until the server has them. */
    @Query("DELETE FROM sections WHERE id NOT IN (:keep) AND id NOT LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun deleteOthers(keep: List<String>)

    @Query("UPDATE sections SET id = :new WHERE id = :old")
    suspend fun renameId(old: String, new: String)

    @Query("SELECT * FROM sections WHERE id LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun unsent(): List<SectionEntity>

    @Query("UPDATE sections SET projectId = :new WHERE projectId = :old")
    suspend fun moveProject(old: String, new: String)
}

@Dao
interface ConflictDao {
    @Upsert
    suspend fun upsert(conflict: Conflict)

    @Query("DELETE FROM conflicts WHERE taskId = :taskId")
    suspend fun delete(taskId: String)

    @Query("SELECT * FROM conflicts")
    suspend fun all(): List<Conflict>

    @Query("SELECT * FROM conflicts")
    fun observeAll(): Flow<List<Conflict>>
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isInbox = 1 LIMIT 1")
    suspend fun inbox(): ProjectEntity?

    @Query("SELECT * FROM projects")
    suspend fun all(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<ProjectEntity?>

    @Query("UPDATE projects SET id = :new WHERE id = :old")
    suspend fun renameId(old: String, new: String)

    @Query("SELECT * FROM projects WHERE isInbox = 0 AND id LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun unsent(): List<ProjectEntity>

    @Query("UPDATE projects SET parentId = :new WHERE parentId = :old")
    suspend fun renameParent(old: String, new: String)

    @Upsert
    suspend fun upsert(projects: List<ProjectEntity>)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Projects made or changed on the phone stay until the server has them, and so do projects
     * archived here: the server stops listing them, and they're what Archived shows.
     */
    @Query("DELETE FROM projects WHERE id NOT IN (:keep) AND id NOT LIKE 'local:%' AND isArchived = 0 AND deletedAt IS NULL AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun deleteOthers(keep: List<String>)

    @Query("SELECT * FROM projects ORDER BY `order`")
    fun observeAll(): Flow<List<ProjectEntity>>

    /** Projects in Trash, without sub-projects of one that's in Trash too. */
    @Query("SELECT * FROM projects WHERE deletedAt IS NOT NULL AND (parentId IS NULL OR parentId NOT IN (SELECT id FROM projects WHERE deletedAt IS NOT NULL)) ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<ProjectEntity>>
}

@Dao
interface LabelDao {
    @Upsert
    suspend fun upsert(labels: List<LabelEntity>)

    @Query("SELECT * FROM labels")
    suspend fun all(): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE id = :id")
    suspend fun get(id: String): LabelEntity?

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM labels WHERE id NOT IN (:keep) AND id NOT LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun deleteOthers(keep: List<String>)

    @Query("UPDATE labels SET id = :new WHERE id = :old")
    suspend fun renameId(old: String, new: String)

    @Query("SELECT * FROM labels WHERE id LIKE 'local:%' AND id NOT IN (SELECT taskId FROM pending_changes)")
    suspend fun unsent(): List<LabelEntity>

    @Query("SELECT * FROM labels ORDER BY `order`")
    fun observeAll(): Flow<List<LabelEntity>>
}

class Converters {
    // Server ids are UUIDs, so a comma can't appear inside one.
    @TypeConverter fun fromIds(ids: List<String>): String = ids.joinToString(",")
    @TypeConverter fun toIds(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(",")
}

@Database(
    entities = [
        TaskEntity::class, ProjectEntity::class, LabelEntity::class, PendingChange::class, Conflict::class, SectionEntity::class,
        CommentEntity::class, ReminderEntity::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7),
    ],
)
@TypeConverters(Converters::class)
abstract class ConatusDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun projects(): ProjectDao
    abstract fun labels(): LabelDao
    abstract fun changes(): PendingChangeDao
    abstract fun conflicts(): ConflictDao
    abstract fun sections(): SectionDao
    abstract fun comments(): CommentDao
    abstract fun reminders(): ReminderDao

    companion object {
        fun create(context: Context): ConatusDatabase =
            Room.databaseBuilder(context, ConatusDatabase::class.java, "conatus.db").build()
    }
}
