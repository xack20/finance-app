package app.hisaab.capture.agent

import app.hisaab.agent.ProposedWrite
import app.hisaab.agent.WriteDescriptor
import app.hisaab.domain.AccountKind
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.TxnKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** What the capture agent decided about one or more messages of a batch. */
sealed interface CaptureDecision {
    /** The messages this decision settles; several when they are copies of one payment (SMS + email…). */
    val messageIds: List<String>

    data class PostTransaction(
        override val messageIds: List<String>,
        val account: String,
        val amount: Double,
        val kind: TxnKind,
        val category: String? = null,
        val merchant: String? = null,
        val notes: String? = null,
    ) : CaptureDecision

    data class PostTransfer(
        override val messageIds: List<String>,
        val fromAccount: String,
        val toAccount: String,
        val amount: Double,
        val notes: String? = null,
    ) : CaptureDecision

    data class PostLendBorrow(
        override val messageIds: List<String>,
        val person: String,
        val amount: Double,
        val direction: LendBorrowDirection,
        val account: String,
        val purpose: String? = null,
    ) : CaptureDecision

    data class CreateAccount(val name: String, val kind: AccountKind, val institution: String?) : CaptureDecision {
        override val messageIds: List<String> get() = emptyList()
    }

    data class Skip(override val messageIds: List<String>, val reason: String) : CaptureDecision

    data class Ask(override val messageIds: List<String>, val question: String, val options: List<String>) :
        CaptureDecision

    data class Remember(val fact: String) : CaptureDecision {
        override val messageIds: List<String> get() = emptyList()
    }
}

/** Valid [decisions]; [errors] to send back to the agent; batch messages no valid decision covers. */
data class DecisionResult(val decisions: List<CaptureDecision>, val errors: List<String>, val undecided: Set<String>)

/**
 * Turns the agent's proposed writes into checked decisions. Code, not the model, guarantees: every message
 * belongs to the batch and is decided at most once, amounts are positive, kinds are real, and every account
 * exists or is created in the same batch (names compare case-insensitively).
 */
fun parseDecisions(writes: List<ProposedWrite>, batchIds: Set<String>, knownAccounts: Set<String>): DecisionResult {
    val created = writes.filter { it.tool == CREATE_ACCOUNT }.map { parseOne(it, emptySet()) }
    val accounts = knownAccounts.map { it.lowercase() }.toSet() +
        created.mapNotNull { ((it as? Parsed.Ok)?.decision as? CaptureDecision.CreateAccount)?.name?.lowercase() }

    val decided = mutableSetOf<String>()
    val decisions = mutableListOf<CaptureDecision>()
    val errors = mutableListOf<String>()
    writes.forEach { w ->
        when (val parsed = parseOne(w, accounts)) {
            is Parsed.Bad -> errors += parsed.why
            is Parsed.Ok -> {
                val ids = parsed.decision.messageIds
                val outside = ids.filterNot { it in batchIds }
                val twice = ids.filter { it in decided }
                when {
                    outside.isNotEmpty() -> errors += "${w.tool}: message ${outside.joinToString()} is not in this batch"
                    twice.isNotEmpty() -> errors += "${w.tool}: message ${twice.joinToString()} is decided twice"
                    else -> {
                        decided += ids
                        decisions += parsed.decision
                    }
                }
            }
        }
    }
    return DecisionResult(decisions, errors, batchIds - decided)
}

private sealed interface Parsed {
    data class Ok(val decision: CaptureDecision) : Parsed
    data class Bad(val why: String) : Parsed
}

@Suppress("CyclomaticComplexMethod")
private fun parseOne(w: ProposedWrite, accounts: Set<String>): Parsed {
    val a = w.args
    val ids = a.strings("messages")
    fun missing(vararg fields: String) = Parsed.Bad("${w.tool} needs ${fields.joinToString()}${ids.label()}")
    fun badAccount(name: String) =
        Parsed.Bad("${w.tool}${ids.label()}: unknown account '$name' — use an existing account or create_account")
    fun known(name: String) = name.lowercase() in accounts
    val amount = a.dbl("amount")?.takeIf { it > 0.0 && it.isFinite() }
    val badAmount = Parsed.Bad("${w.tool}${ids.label()}: amount must be above 0")

    return when (w.tool) {
        "post_transaction" -> {
            val account = a.str("account")
            val kind = a.str("kind")?.uppercase()?.takeIf { it == "EXPENSE" || it == "INCOME" }
            when {
                ids.isEmpty() || account == null || kind == null -> missing("messages", "account", "kind")
                amount == null -> badAmount
                !known(account) -> badAccount(account)
                else -> Parsed.Ok(
                    CaptureDecision.PostTransaction(
                        ids, account, amount, TxnKind.valueOf(kind), a.str("category"), a.str("merchant"), a.str("notes"),
                    ),
                )
            }
        }
        "post_transfer" -> {
            val from = a.str("fromAccount")
            val to = a.str("toAccount")
            when {
                ids.isEmpty() || from == null || to == null -> missing("messages", "fromAccount", "toAccount")
                amount == null -> badAmount
                !known(from) -> badAccount(from)
                !known(to) -> badAccount(to)
                else -> Parsed.Ok(CaptureDecision.PostTransfer(ids, from, to, amount, a.str("notes")))
            }
        }
        "post_lend_borrow" -> {
            val person = a.str("person")
            val account = a.str("account")
            val direction = a.str("kind")?.uppercase()?.let { k -> LendBorrowDirection.entries.firstOrNull { it.name == k } }
            when {
                ids.isEmpty() || person == null || account == null || direction == null ->
                    missing("messages", "person", "account", "kind")
                amount == null -> badAmount
                !known(account) -> badAccount(account)
                else -> Parsed.Ok(CaptureDecision.PostLendBorrow(ids, person, amount, direction, account, a.str("purpose")))
            }
        }
        CREATE_ACCOUNT -> {
            val name = a.str("name")?.trim()
            val kind = a.str("kind")?.uppercase()?.let { k -> CREATABLE_KINDS.firstOrNull { it.name == k } }
            if (name == null || kind == null) missing("name", "kind (CASH|BANK|MFS|CARD)")
            else Parsed.Ok(CaptureDecision.CreateAccount(name, kind, a.str("institution")))
        }
        "skip" ->
            if (ids.isEmpty()) missing("messages", "reason")
            else Parsed.Ok(CaptureDecision.Skip(ids, a.str("reason") ?: "not a payment"))
        "ask" -> {
            val question = a.str("question")
            if (ids.isEmpty() || question == null) missing("messages", "question")
            else Parsed.Ok(CaptureDecision.Ask(ids, question, a.strings("options")))
        }
        "remember" -> a.str("fact")?.let { Parsed.Ok(CaptureDecision.Remember(it)) } ?: missing("fact")
        else -> Parsed.Bad("unknown decision '${w.tool}'")
    }
}

private const val CREATE_ACCOUNT = "create_account"
private val CREATABLE_KINDS = listOf(AccountKind.CASH, AccountKind.BANK, AccountKind.MFS, AccountKind.CARD)

private fun List<String>.label() = if (isEmpty()) "" else " (messages ${joinToString()})"
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.dbl(key: String): Double? =
    (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()

/** The decisions as the agent sees them in its prompt. */
fun captureDecisionDescriptors(): List<WriteDescriptor> = listOf(
    WriteDescriptor(
        "post_transaction", "Add a payment to the ledger (several messages = copies of the same payment)",
        """{ "messages": [id], "account": string, "amount": number, "kind": "EXPENSE|INCOME", "category": string?, "merchant": string?, "notes": string? }""",
    ),
    WriteDescriptor(
        "post_transfer", "Money moved between two of the user's own accounts (e.g. bank → bKash, card bill payment)",
        """{ "messages": [id], "fromAccount": string, "toAccount": string, "amount": number, "notes": string? }""",
    ),
    WriteDescriptor(
        "post_lend_borrow", "Money lent to or borrowed from a person",
        """{ "messages": [id], "person": string, "amount": number, "kind": "LENT|BORROWED", "account": string, "purpose": string? }""",
    ),
    WriteDescriptor(
        "create_account", "Create an account the messages show the user has (usable by posts in the same reply)",
        """{ "name": string, "kind": "CASH|BANK|MFS|CARD", "institution": string? }""",
    ),
    WriteDescriptor(
        "skip", "Not a payment, or already in the ledger: promo, offer, OTP, balance or due reminder, duplicate…",
        """{ "messages": [id], "reason": string }""",
    ),
    WriteDescriptor(
        "ask", "Only when you truly can't tell: ask the user one short question (options = quick answers)",
        """{ "messages": [id], "question": string, "options": [string]? }""",
    ),
    WriteDescriptor(
        "remember", "Save a lasting fact that will help with future messages (e.g. which account a sender is)",
        """{ "fact": string }""",
    ),
)
