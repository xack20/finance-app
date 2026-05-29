package app.hisaab.llm

import app.hisaab.domain.Category
import kotlin.test.Test
import kotlin.test.assertTrue

class PromptsTest {

    private val categories = listOf(
        cat("food", "Food & dining"),
        cat("transport", "Transport"),
        cat("bills", "Bills"),
        cat("salary", "Salary"),
        cat("other", "Other"),
    )

    private fun cat(id: String, name: String) =
        Category(id = id, name = name, parentId = null, color = null, icon = null, isDefault = true)

    @Test
    fun `extraction prompt lists every category id`() {
        val p = Prompts.extractionSystem(categories)
        categories.forEach { assertTrue(p.contains("\"${it.id}\""), "missing id ${it.id}") }
    }

    @Test
    fun `extraction prompt instructs strict json and isFinancial reject path`() {
        val p = Prompts.extractionSystem(categories)
        assertTrue(p.contains("JSON"))
        assertTrue(p.contains("isFinancial"))
        assertTrue(p.contains("confidence"))
    }

    @Test
    fun `extraction prompt contains a BD few-shot example`() {
        val p = Prompts.extractionSystem(categories)
        assertTrue(p.contains("bKash") || p.contains("Tk") || p.contains("৳"), "no BD few-shot")
    }

    @Test
    fun `on-device short variant is shorter than full and still names ids`() {
        val full = Prompts.extractionSystem(categories)
        val short = Prompts.extractionSystemShort(categories)
        assertTrue(short.length < full.length, "short not shorter")
        assertTrue(short.contains("\"food\""))
        assertTrue(short.contains("JSON"))
    }

    @Test
    fun `categorize prompt names merchant and constrains to ids`() {
        val p = Prompts.categorize("Aarong", categories)
        assertTrue(p.contains("Aarong"))
        assertTrue(p.contains("\"food\""))
    }
}
