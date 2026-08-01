package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class DnsFailure(
  @SerialName("kind")
  public val kind: String,
  @SerialName("host")
  public val host: String,
) : DiagnosticReportFailure
