import java.util.UUID

data class Host(
    var pearName: String,
    var hostName: String,
    var portNumber: Int,
    var uuid: UUID = UUID.nameUUIDFromBytes("$pearName|$hostName|$portNumber".toByteArray())

) {
    override fun toString(): String = "[$uuid] $pearName "
}
