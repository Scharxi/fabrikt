package com.cjbooms.fabrikt.generators.client

import com.cjbooms.fabrikt.cli.ClientCodeGenOptionType
import com.cjbooms.fabrikt.model.SourceApi
import com.cjbooms.fabrikt.util.KaizenParserExtensions.groupByPathSegment
import com.cjbooms.fabrikt.util.KaizenParserExtensions.routeToPathsByFirstTag
import com.reprezen.kaizen.oasparser.model3.Path

object ClientGeneratorUtils {
    fun SourceApi.groupedClientPaths(options: Set<ClientCodeGenOptionType>): Map<String, Map<String, Path>> =
        if (ClientCodeGenOptionType.GROUP_BY_TAG in options) {
            openApi3.routeToPathsByFirstTag()
        } else {
            openApi3.groupByPathSegment()
        }
}
