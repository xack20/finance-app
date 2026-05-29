package app.hisaab.capture

import app.hisaab.domain.Direction

/**
 * Anonymized representative Bangladeshi MFS/bank SMS corpus. The crown-jewel
 * test asset: every BankTemplate change is validated against these. Account
 * numbers / phone numbers are masked; amounts and message structure mirror
 * real bKash / Nagad / Rocket / City Bank / BRAC Bank / DBBL formats.
 *
 * [expected] is the field set a fully-correct BankTemplate.extract() must
 * produce for [body] (after BanglaNumerals.normalize). [complete] marks rows a
 * template should resolve end-to-end (amount + direction present).
 */
object SmsCorpus {

    data class Sample(
        val name: String,
        val sender: String,
        val templateKey: String,
        val body: String,
        val expectedAmount: Double?,
        val expectedDirection: Direction?,
        val expectedRefNo: String?,
        val expectedBalanceAfter: Double?,
        val expectedComplete: Boolean,
    )

    val samples: List<Sample> = listOf(
        // ---- bKash ----
        Sample(
            name = "bkash_received_money",
            sender = "bKash",
            templateKey = "bkash",
            body = "You have received Tk 1,500.00 from 017XXXXXX89. Ref 9A1B2C3D4. Fee Tk 0.00. Balance Tk 3,250.50. TrxID 9A1B2C3D4 at 12/05/2026 14:33",
            expectedAmount = 1500.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "9A1B2C3D4",
            expectedBalanceAfter = 3250.50,
            expectedComplete = true,
        ),
        Sample(
            name = "bkash_payment",
            sender = "bKash",
            templateKey = "bkash",
            body = "Payment Tk 850.00 to SHWAPNO. Fee Tk 0.00. Balance Tk 2,400.50. TrxID 8K2L9M0P1 at 13/05/2026 10:05",
            expectedAmount = 850.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "8K2L9M0P1",
            expectedBalanceAfter = 2400.50,
            expectedComplete = true,
        ),
        Sample(
            name = "bkash_cashout",
            sender = "bKash",
            templateKey = "bkash",
            body = "Cash Out Tk 2,000.00 to Agent 017XXXXXX12. Fee Tk 36.50. Balance Tk 364.00. TrxID 7C3D5E6F8 at 14/05/2026 18:20",
            expectedAmount = 2000.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "7C3D5E6F8",
            expectedBalanceAfter = 364.00,
            expectedComplete = true,
        ),
        Sample(
            name = "bkash_send_money",
            sender = "bKash",
            templateKey = "bkash",
            body = "Send Money Tk 500.00 to 018XXXXXX44. Fee Tk 5.00. Balance Tk 1,000.00. TrxID 6B2A1Z9Y0 at 15/05/2026 09:11",
            expectedAmount = 500.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "6B2A1Z9Y0",
            expectedBalanceAfter = 1000.00,
            expectedComplete = true,
        ),

        // ---- Nagad ----
        Sample(
            name = "nagad_received",
            sender = "NAGAD",
            templateKey = "nagad",
            body = "Money Received. Amount: Tk 3,000.00 Sender: 019XXXXXX33 Ref: NGD55667 Balance: Tk 5,120.75 TxnID: NGD55667 13/05/2026 11:42",
            expectedAmount = 3000.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "NGD55667",
            expectedBalanceAfter = 5120.75,
            expectedComplete = true,
        ),
        Sample(
            name = "nagad_payment",
            sender = "NAGAD",
            templateKey = "nagad",
            body = "Payment Successful. Amount: Tk 1,200.00 To: DARAZ Balance: Tk 3,920.75 TxnID: NGD77881 13/05/2026 16:02",
            expectedAmount = 1200.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "NGD77881",
            expectedBalanceAfter = 3920.75,
            expectedComplete = true,
        ),
        Sample(
            name = "nagad_cashout",
            sender = "NAGAD",
            templateKey = "nagad",
            body = "Cash Out. Amount: Tk 1,000.00 Charge: Tk 11.50 Balance: Tk 2,898.25 TxnID: NGD90011 14/05/2026 19:47",
            expectedAmount = 1000.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "NGD90011",
            expectedBalanceAfter = 2898.25,
            expectedComplete = true,
        ),

        // ---- Rocket (DBBL Mobile Banking) ----
        Sample(
            name = "rocket_credited",
            sender = "Rocket",
            templateKey = "rocket",
            body = "Tk 2,500.00 credited to your A/C. TxnId 4455667788. Balance Tk 6,000.00. 12/05/2026 13:00",
            expectedAmount = 2500.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "4455667788",
            expectedBalanceAfter = 6000.00,
            expectedComplete = true,
        ),
        Sample(
            name = "rocket_debited",
            sender = "Rocket",
            templateKey = "rocket",
            body = "Tk 750.00 debited from your A/C for payment. TxnId 9988776655. Balance Tk 5,250.00. 12/05/2026 13:30",
            expectedAmount = 750.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "9988776655",
            expectedBalanceAfter = 5250.00,
            expectedComplete = true,
        ),

        // ---- City Bank ----
        Sample(
            name = "citybank_debit_card",
            sender = "City Bank",
            templateKey = "citybank",
            body = "Your A/C XXXX1234 is debited BDT 4,300.00 on 12-05-2026 at AGORA. Avail Bal BDT 18,700.00. Ref 30021145",
            expectedAmount = 4300.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "30021145",
            expectedBalanceAfter = 18700.00,
            expectedComplete = true,
        ),
        Sample(
            name = "citybank_credit_salary",
            sender = "City Bank",
            templateKey = "citybank",
            body = "Your A/C XXXX1234 is credited BDT 55,000.00 on 01-05-2026 SALARY. Avail Bal BDT 73,700.00. Ref 30019902",
            expectedAmount = 55000.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "30019902",
            expectedBalanceAfter = 73700.00,
            expectedComplete = true,
        ),

        // ---- BRAC Bank ----
        Sample(
            name = "bracbank_debit",
            sender = "BRAC BANK",
            templateKey = "bracbank",
            body = "Dear Customer, BDT 6,750.00 has been debited from A/C XXXX9988 on 12-05-2026. Available Balance BDT 41,250.00. TXN 7781234",
            expectedAmount = 6750.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "7781234",
            expectedBalanceAfter = 41250.00,
            expectedComplete = true,
        ),
        Sample(
            name = "bracbank_credit",
            sender = "BRAC BANK",
            templateKey = "bracbank",
            body = "Dear Customer, BDT 12,000.00 has been credited to A/C XXXX9988 on 13-05-2026. Available Balance BDT 53,250.00. TXN 7785678",
            expectedAmount = 12000.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "7785678",
            expectedBalanceAfter = 53250.00,
            expectedComplete = true,
        ),

        // ---- DBBL (Dutch-Bangla Bank) ----
        Sample(
            name = "dbbl_debit",
            sender = "DBBL",
            templateKey = "dbbl",
            body = "Dear Customer, your account XXXX5566 has been debited by Tk 980.00 on 12/05/2026. Balance Tk 22,020.00. Ref DB445566",
            expectedAmount = 980.00,
            expectedDirection = Direction.DEBIT,
            expectedRefNo = "DB445566",
            expectedBalanceAfter = 22020.00,
            expectedComplete = true,
        ),
        Sample(
            name = "dbbl_credit",
            sender = "DBBL",
            templateKey = "dbbl",
            body = "Dear Customer, your account XXXX5566 has been credited by Tk 7,500.00 on 14/05/2026. Balance Tk 29,520.00. Ref DB447788",
            expectedAmount = 7500.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "DB447788",
            expectedBalanceAfter = 29520.00,
            expectedComplete = true,
        ),

        // ---- Bangla numeral variant (bKash) ----
        Sample(
            name = "bkash_bangla_digits",
            sender = "bKash",
            templateKey = "bkash",
            body = "You have received Tk ১,২৫০.০০ from 017XXXXXX01. Balance Tk ৪,৫০০.০০. TrxID 5Z6Y7X8W9 at 16/05/2026 08:00",
            expectedAmount = 1250.00,
            expectedDirection = Direction.CREDIT,
            expectedRefNo = "5Z6Y7X8W9",
            expectedBalanceAfter = 4500.00,
            expectedComplete = true,
        ),

        // ---- Partial / incomplete (template can't fully resolve -> falls to LLM) ----
        Sample(
            name = "bkash_promo_no_amount",
            sender = "bKash",
            templateKey = "bkash",
            body = "Recharge any operator from bKash and get cashback! Dial *247#.",
            expectedAmount = null,
            expectedDirection = null,
            expectedRefNo = null,
            expectedBalanceAfter = null,
            expectedComplete = false,
        ),

        // ---- Non-financial promo (pre-filter should drop) ----
        Sample(
            name = "robi_promo_nonfinancial",
            sender = "ROBI",
            templateKey = "",
            body = "Enjoy 5GB internet at Tk 199! Buy now, valid 30 days. Dial *123#.",
            expectedAmount = null,
            expectedDirection = null,
            expectedRefNo = null,
            expectedBalanceAfter = null,
            expectedComplete = false,
        ),
    )

    fun byName(name: String): Sample = samples.first { it.name == name }
    fun byTemplate(key: String): List<Sample> = samples.filter { it.templateKey == key }
}
