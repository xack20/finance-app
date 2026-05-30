// Tools.kt
package app.hisaab.agent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A staged write the model proposed (NEVER executed in the loop; committed in M4-2). */
@Serializable
data class ProposedWrite(val tool: String, val args: JsonObject)

/** A side-effect-free tool the loop may execute. Returns a compact JSON string fed back to the model. */
interface ReadTool {
    val name: String
    val description: String          // one line, injected into the prompt
    val paramsDoc: String            // e.g. "{ \"period\": \"YYYY-MM\" }"
    suspend fun execute(args: JsonObject): String
}

/** Prompt-only descriptor for a write tool (execution lands in M4-2). */
data class WriteDescriptor(val name: String, val description: String, val paramsDoc: String)

class ToolRegistry(
    val readTools: List<ReadTool>,
    val writeDescriptors: List<WriteDescriptor>,
) {
    private val readByName = readTools.associateBy { it.name }
    private val writeNames = writeDescriptors.map { it.name }.toSet()
    fun read(name: String): ReadTool? = readByName[name]
    fun isWrite(name: String): Boolean = name in writeNames
    fun isKnown(name: String): Boolean = name in readByName || name in writeNames
}
