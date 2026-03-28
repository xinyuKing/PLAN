package com.example.planreminder.agent

import com.example.planreminder.i18n.AppLanguage
import com.example.planreminder.i18n.promptLanguageName
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class QwenPlanAgentClient {
    private val httpClient = OkHttpClient()

    suspend fun continueConversation(
        settings: AgentSettings,
        history: List<AgentMessage>,
        draft: AgentPlanDraft,
        language: AppLanguage,
    ): QwenAgentReply = withContext(Dispatchers.IO) {
        val recentHistory = history.takeLast(MAX_HISTORY_MESSAGES)
        val messages = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "system")
                    .put("content", buildSystemPrompt(draft, language)),
            )
            recentHistory.forEach { message ->
                put(
                    JSONObject()
                        .put("role", if (message.role == AgentMessageRole.USER) "user" else "assistant")
                        .put("content", message.text),
                )
            }
        }

        val requestBody = JSONObject()
            .put("model", settings.model)
            .put("temperature", 0.2)
            .put("messages", messages)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(chatCompletionsUrl(settings.baseUrl))
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            throw IOException(
                "Qwen 请求失败：HTTP ${response.code} ${response.message}${if (errorBody.isNotBlank()) " - $errorBody" else ""}",
            )
        }

        val responseText = response.body?.string().orEmpty()
        val root = JSONObject(responseText)
        val content = root
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")

        parseAgentReply(content, draft, language)
    }

    private fun buildSystemPrompt(draft: AgentPlanDraft, language: AppLanguage): String {
        val now = ZonedDateTime.now()
        return """
            你是一个“计划提醒填写助手”，负责从用户的自然语言里提取计划字段，并继续追问缺失信息。
            你的输出必须始终是 JSON，不能带 markdown，不能带代码块，不能带额外说明。

            当前时间：${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX"))}
            当前时区：${now.zone.id}

            目标字段：
            - title：事项内容
            - location：地点，可留空，不填地址也可以直接确认
            - date：日期，格式必须是 yyyy-MM-dd
            - time：时间，格式必须是 HH:mm，24 小时制

            当前草稿：
            - title=${draft.title.ifBlank { "(空)" }}
            - location=${draft.location.ifBlank { "(空)" }}
            - date=${draft.date.ifBlank { "(空)" }}
            - time=${draft.time.ifBlank { "(空)" }}

            规则：
            1. 如果用户提供了新的字段值，应更新对应字段。
            2. location 不是必填项；如果用户没有说地点，不要反复追问地点。
            3. 如果信息不全，assistantMessage 要明确追问最缺的字段。
            4. 如果用户说“明天”“后天”“下周一”等相对时间，必须转换成绝对日期 yyyy-MM-dd。
            5. 只有 title、date、time 三项完整且合理时，readyForConfirmation 才能是 true。
            6. missingFields 只能包含 title、date、time。
            7. assistantMessage 必须使用 ${language.promptLanguageName()}。

            JSON 结构必须严格如下：
            {
              "assistantMessage": "字符串",
              "draft": {
                "title": "字符串，可为空",
                "location": "字符串，可为空",
                "date": "yyyy-MM-dd，可为空",
                "time": "HH:mm，可为空"
              },
              "missingFields": ["title", "date"],
              "readyForConfirmation": false
            }
        """.trimIndent()
    }

    private fun parseAgentReply(
        content: String,
        currentDraft: AgentPlanDraft,
        language: AppLanguage,
    ): QwenAgentReply {
        val jsonText = extractJson(content)
        val root = JSONObject(jsonText)
        val draftJson = root.optJSONObject("draft") ?: JSONObject()
        val newDraft = currentDraft.merge(
            AgentPlanDraft(
                title = draftJson.optString("title").trim(),
                location = draftJson.optString("location").trim(),
                date = draftJson.optString("date").trim(),
                time = draftJson.optString("time").trim(),
            ),
        )

        val missingFromModel = buildList {
            val array = root.optJSONArray("missingFields") ?: JSONArray()
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value in ALLOWED_FIELDS) add(value)
            }
        }

        val normalizedMissing = if (missingFromModel.isNotEmpty()) {
            missingFromModel.distinct()
        } else {
            calculateMissingFields(newDraft)
        }

        val readyForConfirmation = root.optBoolean("readyForConfirmation", false) &&
            normalizedMissing.isEmpty()

        return QwenAgentReply(
            assistantMessage = root.optString("assistantMessage").ifBlank {
                defaultAssistantMessage(readyForConfirmation, language)
            },
            draft = newDraft,
            missingFields = normalizedMissing,
            readyForConfirmation = readyForConfirmation,
        )
    }

    private fun calculateMissingFields(draft: AgentPlanDraft): List<String> {
        return buildList {
            if (draft.title.isBlank()) add("title")
            if (draft.date.isBlank()) add("date")
            if (draft.time.isBlank()) add("time")
        }
    }

    private fun extractJson(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) {
            throw IOException("Qwen 返回的内容不是有效 JSON：$content")
        }
        return content.substring(start, end + 1)
    }

    private fun chatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun defaultAssistantMessage(readyForConfirmation: Boolean, language: AppLanguage): String {
        return when (language) {
            AppLanguage.SIMPLIFIED_CHINESE -> {
                if (readyForConfirmation) "我已经把计划信息整理好了，你可以直接确认。" else "我还需要补充一些信息，请继续告诉我。"
            }
            AppLanguage.TRADITIONAL_CHINESE -> {
                if (readyForConfirmation) "我已經把計劃資訊整理好了，你可以直接確認。" else "我還需要補充一些資訊，請繼續告訴我。"
            }
            AppLanguage.ENGLISH -> {
                if (readyForConfirmation) "I have organized the plan details. You can confirm directly now." else "I still need a bit more information. Please continue."
            }
        }
    }

    companion object {
        private val ALLOWED_FIELDS = setOf("title", "date", "time")
        private const val MAX_HISTORY_MESSAGES = 8
    }
}
