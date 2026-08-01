package examples.enumExamples.models

import com.fasterxml.jackson.`annotation`.JsonValue
import kotlin.String
import kotlin.collections.Map

public enum class RestartPolicyName(
  @JsonValue
  public val `value`: String,
) {
  EMPTY(""),
  NO("no"),
  ALWAYS("always"),
  UNLESS_STOPPED("unless-stopped"),
  ON_FAILURE("on-failure"),
  ;

  override fun toString(): String = value

  public companion object {
    private val mapping: Map<String, RestartPolicyName> =
        entries.associateBy(RestartPolicyName::value)

    public fun fromValue(`value`: String): RestartPolicyName? = mapping[value]
  }
}
