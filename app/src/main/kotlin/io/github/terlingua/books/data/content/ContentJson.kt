package io.github.terlingua.books.data.content

import io.github.terlingua.books.domain.Block
import io.github.terlingua.books.domain.Inline
import io.github.terlingua.books.domain.Question
import io.github.terlingua.books.domain.QuestionOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object ContentJson {
    private val json = Json {
        classDiscriminator = "t"
        ignoreUnknownKeys = true
    }
    private val blockList = ListSerializer(Block.serializer())
    private val questionList = ListSerializer(QuestionJson.serializer())
    private val stringList = ListSerializer(String.serializer())
    private val stringMap = MapSerializer(String.serializer(), String.serializer())

    fun blocks(text: String): List<Block> = json.decodeFromString(blockList, text)

    fun questions(text: String): List<Question> = json.decodeFromString(questionList, text).map { q ->
        Question(
            id = q.id,
            multiple = q.type == "multiple",
            prompt = q.prompt,
            explanation = q.explanation,
            options = q.options.map { QuestionOption(it.id, it.text, it.correct) },
        )
    }

    fun strings(text: String?): List<String> = text?.let { json.decodeFromString(stringList, it) }.orEmpty()

    fun stringMap(text: String?): Map<String, String> = text?.let { json.decodeFromString(stringMap, it) }.orEmpty()

    @Serializable
    private data class QuestionJson(
        val id: String,
        val type: String = "single",
        val prompt: List<Inline>,
        val explanation: List<Block> = emptyList(),
        val options: List<OptionJson>,
    )

    @Serializable
    private data class OptionJson(val id: String, val text: List<Inline>, val correct: Boolean = false)
}
