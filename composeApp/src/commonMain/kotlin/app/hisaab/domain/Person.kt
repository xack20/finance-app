package app.hisaab.domain

data class Person(
    val id: String,
    val name: String,
    val contactRef: String?,
)

data class PersonWithBalance(
    val person: Person,
    val balance: Double,
)
