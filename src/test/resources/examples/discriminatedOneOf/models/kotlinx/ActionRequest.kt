package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ActionRequest(
  @SerialName("id")
  public val id: String? = null,
  @SerialName("action")
  public val action: ParentAction? = null,
)
