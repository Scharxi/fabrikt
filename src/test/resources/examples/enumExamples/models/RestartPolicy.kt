package examples.enumExamples.models

import com.fasterxml.jackson.`annotation`.JsonProperty
import jakarta.validation.constraints.NotNull
import kotlin.Int

public data class RestartPolicy(
  @param:JsonProperty("Name")
  @get:JsonProperty("Name")
  @get:NotNull
  public val name: RestartPolicyName = RestartPolicyName.EMPTY,
  @param:JsonProperty("MaximumRetryCount")
  @get:JsonProperty("MaximumRetryCount")
  public val maximumRetryCount: Int? = null,
)
