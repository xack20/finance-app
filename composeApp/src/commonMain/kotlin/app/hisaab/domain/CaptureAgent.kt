package app.hisaab.domain

/** Something the capture agent couldn't work out and asked the user; its messages wait as PENDING. */
data class AgentQuestion(
    val id: String,
    val question: String,
    /** Quick answers the agent suggested (may be empty — the user can always type). */
    val options: List<String>,
    val answer: String?,
    val createdAt: Long,
)

/** A fact the capture agent learned ("alerts@citybank.com is the City Bank card"), sent with every batch. */
data class AgentMemory(val id: String, val fact: String, val createdAt: Long)
