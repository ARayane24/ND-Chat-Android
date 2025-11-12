import java.util.UUID

data class Host(
    val pearName: String,
    val hostName: String,
    val portNumber: Int
) {
    val uuid: UUID = UUID.nameUUIDFromBytes("$pearName|$hostName|$portNumber".toByteArray())

    override fun toString(): String = "$pearName[$uuid]@$hostName:$portNumber"
}
