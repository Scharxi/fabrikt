package examples.discriminatedOneOf.models

import kotlin.String
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@JsonClassDiscriminator("errorCode")
@ExperimentalSerializationApi
@Serializable
public sealed class DiscriminatedBase(
  public open val message: String,
) {
  public abstract val errorCode: String
}
