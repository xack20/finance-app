package app.hisaab.domain

data class Category(
    val id: String,
    val name: String,
    val parentId: String?,
    val color: String?,
    val icon: String?,
    val isDefault: Boolean,
)
