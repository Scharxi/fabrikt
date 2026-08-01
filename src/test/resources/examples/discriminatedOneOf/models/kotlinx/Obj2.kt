package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("obj2")
@Serializable
public data class Obj2(
  @SerialName("id2")
  public val id2: String,
) : Poly1
