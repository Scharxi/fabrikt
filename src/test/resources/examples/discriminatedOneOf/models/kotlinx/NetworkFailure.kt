package examples.discriminatedOneOf.models

import kotlin.Int
import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class NetworkFailure(
  @SerialName("kind")
  public val kind: String,
  @SerialName("retries")
  public val retries: Int,
) : DiagnosticReportFailure
