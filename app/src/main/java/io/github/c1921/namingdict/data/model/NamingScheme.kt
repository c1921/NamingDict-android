package io.github.c1921.namingdict.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class GivenNameMode {
    Single,
    Double
}

@Serializable
data class NamingScheme(
    val id: Long = 0L,
    val givenNameMode: GivenNameMode = GivenNameMode.Double,
    val slot1: String = "",
    val slot2: String = ""
)
