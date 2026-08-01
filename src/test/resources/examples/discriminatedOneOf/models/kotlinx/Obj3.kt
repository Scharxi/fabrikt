package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("obj3")
@Serializable
public data class Obj3(
  @SerialName("id3")
  public val id3: String,
) : Poly2
