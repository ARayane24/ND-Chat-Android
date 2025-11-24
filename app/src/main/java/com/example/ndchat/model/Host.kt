import java.util.UUID

data class Host(
    var pearName: String,
    var hostName: String,
    var portNumber: Int
) {
    val uuid: UUID = UUID.nameUUIDFromBytes("$pearName|$hostName|$portNumber".toByteArray())

    override fun toString(): String = "[$uuid] $pearName "
}
