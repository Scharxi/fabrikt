package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("b1")
@Serializable
public data class StateB1(
  @SerialName("mode")
  public val mode: String,
) : StateB
