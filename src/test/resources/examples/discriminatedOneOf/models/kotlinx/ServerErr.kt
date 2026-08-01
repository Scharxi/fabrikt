package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ServerErr(
  @SerialName("message")
  public val message: String,
  @SerialName("errorType")
  public val errorType: BaseErrorErrorType,
  @SerialName("stackTrace")
  public val stackTrace: String,
) : ErrorWrapperError
