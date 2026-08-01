package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class BaseError(
  @SerialName("message")
  public val message: String,
  @SerialName("errorType")
  public val errorType: BaseErrorErrorType,
)
