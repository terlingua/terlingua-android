package io.github.terlingua.books.data.progress

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "book_state")
data class BookStateEntity(
    @PrimaryKey val bookSlug: String,
    val lastSectionKey: String?,
    val updatedAt: Long,
)

@Entity(tableName = "section_progress", indices = [Index("bookSlug")])
data class SectionProgressEntity(
    @PrimaryKey val sectionKey: String,
    val bookSlug: String,
    val firstVisitedAt: Long?,
    val updatedAt: Long,
)

@Entity(
    tableName = "question_progress",
    primaryKeys = ["sectionKey", "questionId"],
    indices = [Index("bookSlug")],
)
data class QuestionProgressEntity(
    val sectionKey: String,
    val questionId: String,
    val bookSlug: String,
    val answeredCorrectly: Boolean,
    /** Comma-separated option ids; ids are validated at build time and never contain commas. */
    val lastSelection: String,
    val selectedAt: Long,
    val updatedAt: Long,
)

@Dao
abstract class ProgressDao {
    @Query("SELECT * FROM book_state")
    abstract fun observeBooks(): Flow<List<BookStateEntity>>

    @Query("SELECT * FROM section_progress")
    abstract fun observeSections(): Flow<List<SectionProgressEntity>>

    @Query("SELECT * FROM question_progress")
    abstract fun observeQuestions(): Flow<List<QuestionProgressEntity>>

    @Query("SELECT * FROM book_state WHERE bookSlug = :slug")
    abstract suspend fun book(slug: String): BookStateEntity?

    @Query("SELECT * FROM section_progress WHERE sectionKey = :key")
    abstract suspend fun section(key: String): SectionProgressEntity?

    @Query("SELECT * FROM question_progress WHERE sectionKey = :key AND questionId = :id")
    abstract suspend fun question(key: String, id: String): QuestionProgressEntity?

    @Query("SELECT * FROM book_state")
    abstract suspend fun allBooks(): List<BookStateEntity>

    @Query("SELECT * FROM section_progress")
    abstract suspend fun allSections(): List<SectionProgressEntity>

    @Query("SELECT * FROM question_progress")
    abstract suspend fun allQuestions(): List<QuestionProgressEntity>

    @Upsert
    abstract suspend fun upsertBook(book: BookStateEntity)

    @Upsert
    abstract suspend fun upsertSections(sections: List<SectionProgressEntity>)

    @Upsert
    abstract suspend fun upsertQuestions(questions: List<QuestionProgressEntity>)

    @Query("DELETE FROM book_state WHERE bookSlug = :slug")
    abstract suspend fun deleteBook(slug: String)

    @Query("DELETE FROM section_progress WHERE bookSlug = :slug")
    abstract suspend fun deleteSections(slug: String)

    @Query("DELETE FROM question_progress WHERE bookSlug = :slug")
    abstract suspend fun deleteQuestions(slug: String)

    @Transaction
    open suspend fun recordAnswer(book: BookStateEntity, section: SectionProgressEntity, question: QuestionProgressEntity) {
        upsertBook(book)
        upsertSections(listOf(section))
        upsertQuestions(listOf(question))
    }

    @Transaction
    open suspend fun recordVisit(book: BookStateEntity, section: SectionProgressEntity) {
        upsertBook(book)
        upsertSections(listOf(section))
    }

    @Transaction
    open suspend fun writeBooks(
        replaceSlugs: List<String>,
        books: List<BookStateEntity>,
        sections: List<SectionProgressEntity>,
        questions: List<QuestionProgressEntity>,
    ) {
        replaceSlugs.forEach {
            deleteQuestions(it)
            deleteSections(it)
            deleteBook(it)
        }
        books.forEach { upsertBook(it) }
        upsertSections(sections)
        upsertQuestions(questions)
    }
}

@Database(
    entities = [BookStateEntity::class, SectionProgressEntity::class, QuestionProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}
