package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class DiscriminatedChild(
  @SerialName("message")
  override val message: String,
) : DiscriminatedBase(message)
