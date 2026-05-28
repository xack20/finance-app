package app.hisaab.domain

data class Merchant(
    val id: String,
    val name: String,
    val normalizedName: String,
    val defaultCategoryId: String?,
)

fun normalizeMerchantName(raw: String): String =
    raw.trim().lowercase().replace(Regex("\\s+"), " ")
