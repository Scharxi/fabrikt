package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("b2")
@Serializable
public data class StateB2(
  @SerialName("mode")
  public val mode: String,
) : StateB
