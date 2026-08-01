package examples.discriminatedOneOf.models

import kotlin.collections.List
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class SomeObj(
  @SerialName("state")
  public val state: State,
  @SerialName("arrayOfStates")
  public val arrayOfStates: List<State>? = null,
  @SerialName("inlinedArray")
  public val inlinedArray: List<SomeObjInlinedArray>? = null,
  @SerialName("inlinedObject")
  public val inlinedObject: SomeObjInlinedObject? = null,
  @SerialName("inlinedObjectNoMappings")
  public val inlinedObjectNoMappings: SomeObjInlinedObjectNoMappings? = null,
)
