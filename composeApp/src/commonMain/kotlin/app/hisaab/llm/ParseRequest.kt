package app.hisaab.llm

import app.hisaab.domain.Category

/**
 * Input to an LlmProvider.parse() call. [text] is the (already redacted on the
 * cloud path) SMS body. [senderHint] is the raw sender id ("bKash", "01..."),
 * used as a parsing hint only. [categories] is the closed set of valid category
 * ids the model must map into (or return null).
 */
data class ParseRequest(
    val text: String,
    val senderHint: String?,
    val categories: List<Category>,
)
