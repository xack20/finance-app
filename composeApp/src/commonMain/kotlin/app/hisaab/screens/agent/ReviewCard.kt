package app.hisaab.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hisaab.agent.ProposedWrite
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.screens.entry.AmountField
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * ReviewCard — displays one row per [ProposedWrite] with:
 *  - an include Checkbox (checked = index in [included]) → [onToggle]
 *  - a human-readable label from [summarize]
 *  - an editable AmountField for writes that carry an "amount" arg → [onEdit]
 *  - a single Apply button → [onApply], disabled when [included] is empty
 */
@Composable
fun ReviewCard(
    writes: List<ProposedWrite>,
    included: Set<Int>,
    onToggle: (Int) -> Unit,
    onEdit: (Int, JsonObject) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHisaabPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.rule, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            text = "Review & apply",
            color = palette.onBackground,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(10.dp))

        writes.forEachIndexed { index, write ->
            val isIncluded = index in included
            val hasAmount = argStr(write, "amount") != null
            val amountStr = argStr(write, "amount") ?: ""

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isIncluded,
                        onCheckedChange = { onToggle(index) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = palette.accent,
                            uncheckedColor = palette.muted,
                            checkmarkColor = palette.background,
                        ),
                        modifier = Modifier.testTag("review_toggle_$index"),
                    )
                    Text(
                        text = summarize(write),
                        color = palette.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (hasAmount) {
                    AmountField(
                        value = amountStr,
                        onChange = { newAmt -> onEdit(index, write.withAmount(newAmt)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, end = 4.dp)
                            .testTag("review_amount_$index"),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onApply,
            enabled = included.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                disabledContainerColor = palette.rule,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("review_apply"),
        ) {
            Text(
                text = "Apply",
                color = if (included.isNotEmpty()) palette.background else palette.muted,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Reads a primitive arg value as a string, or null if not present / not a primitive. */
private fun argStr(w: ProposedWrite, key: String): String? =
    (w.args[key] as? JsonPrimitive)?.content

/**
 * Produces a human-readable one-liner for each write tool kind.
 *
 * add_transaction     → "Expense ৳500 · Cash · Food"
 * transfer            → "Transfer ৳1000 · Bank → Cash"
 * record_card_payment → "Card payment ৳500 · VISA from Bank"
 * record_lend_borrow  → "Lent ৳2000 to Karim" / "Borrowed ৳2000 from Karim"
 * create_account      → "New account: VISA (CARD)"
 * create_category     → "New category: Food"
 */
private fun summarize(w: ProposedWrite): String = when (w.tool) {
    "add_transaction" -> {
        val kind = argStr(w, "kind")?.lowercase() ?: "transaction"
        val label = when (kind) {
            "expense" -> "Expense"
            "income"  -> "Income"
            else      -> kind.replaceFirstChar { it.uppercase() }
        }
        val amount   = argStr(w, "amount")?.let { "৳$it" } ?: ""
        val account  = argStr(w, "account") ?: ""
        val category = argStr(w, "category") ?: ""
        listOf(label, amount, account, category)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    "transfer" -> {
        val amount = argStr(w, "amount")?.let { "৳$it" } ?: ""
        val from   = argStr(w, "fromAccount") ?: ""
        val to     = argStr(w, "toAccount") ?: ""
        val route  = if (from.isNotBlank() && to.isNotBlank()) "$from → $to" else "$from$to"
        listOf("Transfer", amount, route)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    "record_card_payment" -> {
        val amount  = argStr(w, "amount")?.let { "৳$it" } ?: ""
        val card    = argStr(w, "card") ?: ""
        val account = argStr(w, "fromAccount") ?: ""
        val detail  = listOf(card, account).filter { it.isNotBlank() }.joinToString(" from ")
        listOf("Card payment", amount, detail)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    "record_lend_borrow" -> {
        val kind   = argStr(w, "kind")?.lowercase() ?: "lend"
        val verb   = if (kind == "borrow") "Borrowed" else "Lent"
        val prep   = if (kind == "borrow") "from" else "to"
        val amount = argStr(w, "amount")?.let { "৳$it" } ?: ""
        val person = argStr(w, "person") ?: ""
        val personPart = if (person.isNotBlank()) "$prep $person" else ""
        listOf(verb, amount, personPart)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    "create_account" -> {
        val name = argStr(w, "name") ?: "account"
        val kind = argStr(w, "kind")?.uppercase() ?: ""
        val kindPart = if (kind.isNotBlank()) "($kind)" else ""
        "New account: $name $kindPart".trim()
    }

    "create_category" -> {
        val name   = argStr(w, "name") ?: "category"
        val parent = argStr(w, "parent")
        if (parent != null) "New category: $name (under $parent)" else "New category: $name"
    }

    else -> w.tool
}

/**
 * Returns a new [JsonObject] identical to [this.args] but with the "amount" key replaced
 * by the numeric value of [amount] (if parseable as Double), or omitted if blank/invalid.
 */
private fun ProposedWrite.withAmount(amount: String): JsonObject = buildJsonObject {
    args.forEach { (k, v) ->
        if (k != "amount") put(k, v)
    }
    amount.toDoubleOrNull()?.let { put("amount", it) }
}
