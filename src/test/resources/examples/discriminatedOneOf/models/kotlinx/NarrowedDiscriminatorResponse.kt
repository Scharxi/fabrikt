package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class NarrowedDiscriminatorResponse(
  @SerialName("errorCode")
  public val errorCode: String,
  @SerialName("message")
  public val message: String,
)
