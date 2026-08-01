package com.cjbooms.fabrikt.generators.server

import com.cjbooms.fabrikt.model.ControllerType
import com.cjbooms.fabrikt.util.NormalisedString.camelCase
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.SecurityRequirement

object ServerSecurity {
    fun controllerName(resourceName: String) = "$resourceName${ControllerType.SUFFIX}"

    fun methodName(op: Operation, verb: String, isSingleResource: Boolean) =
        op.operationId?.camelCase() ?: httpVerbMethodName(verb, isSingleResource)

    private fun httpVerbMethodName(verb: String, isSingleResource: Boolean) =
        if (isSingleResource) "${verb}ById" else verb

    enum class SecuritySupport(val allowsAuthenticated: Boolean, val allowsAnonymous: Boolean) {
        NO_SECURITY(false, false),
        AUTHENTICATION_REQUIRED(true, false),
        AUTHENTICATION_PROHIBITED(false, true),
        AUTHENTICATION_OPTIONAL(true, true),
    }

    fun List<SecurityRequirement>.securitySupport(): SecuritySupport {
        val containsEmptyObject = any { it.requirements.isEmpty() }
        val containsNonEmptyObject = any { it.requirements.isNotEmpty() }

        return when {
            containsEmptyObject && containsNonEmptyObject -> SecuritySupport.AUTHENTICATION_OPTIONAL
            containsEmptyObject -> SecuritySupport.AUTHENTICATION_PROHIBITED
            containsNonEmptyObject -> SecuritySupport.AUTHENTICATION_REQUIRED
            else -> SecuritySupport.NO_SECURITY
        }
    }

    fun Operation.securitySupport(defaultSupport: SecuritySupport? = null): SecuritySupport {
        if (!hasSecurityRequirements() && defaultSupport != null) {
            return defaultSupport
        }
        return securityRequirements.securitySupport()
    }
}
