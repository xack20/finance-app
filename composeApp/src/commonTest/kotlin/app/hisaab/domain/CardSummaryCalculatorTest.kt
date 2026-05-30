package app.hisaab.domain
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CardSummaryCalculatorTest {
    private fun due(today: String, statementDay: Int, dueDay: Int): LocalDate =
        CardSummaryCalculator.nextDueDate(LocalDate.parse(today), statementDay, dueDay)

    @Test fun `due later in same month when dueDay greater than statementDay`() {
        assertEquals(LocalDate(2026, 3, 20), due("2026-03-10", statementDay = 5, dueDay = 20))
    }
    @Test fun `before this months close due is in the prior cycle Feb`() {
        assertEquals(LocalDate(2026, 2, 20), due("2026-03-03", statementDay = 5, dueDay = 20))
    }
    @Test fun `due advances to next month when dueDay not greater than statementDay`() {
        assertEquals(LocalDate(2026, 4, 10), due("2026-03-26", statementDay = 25, dueDay = 10))
    }
    @Test fun `year rolls over December close to January due`() {
        assertEquals(LocalDate(2027, 1, 10), due("2026-12-27", statementDay = 25, dueDay = 10))
    }
    @Test fun `day clamps to last day of a short month`() {
        assertEquals(LocalDate(2026, 2, 28), due("2026-02-20", statementDay = 15, dueDay = 31))
    }
    @Test fun `leap February clamps to 29`() {
        assertEquals(LocalDate(2028, 2, 29), due("2028-02-20", statementDay = 15, dueDay = 31))
    }
}
