package examples.discriminatedOneOf.models

import kotlin.collections.List
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ResponseWithChildActionsPartial(
  @SerialName("actions")
  public val actions: List<ChildActionsPartial>? = null,
)
