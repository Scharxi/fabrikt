package com.cjbooms.fabrikt.generators.server

import com.cjbooms.fabrikt.cli.ServerCodeGenOptionType
import com.cjbooms.fabrikt.model.SourceApi
import com.cjbooms.fabrikt.util.KaizenParserExtensions.groupByPathSegment
import com.cjbooms.fabrikt.util.KaizenParserExtensions.routeToPathsByFirstTag
import com.reprezen.kaizen.oasparser.model3.Path

object ServerGeneratorUtils {
    fun SourceApi.groupedServerPaths(options: Set<ServerCodeGenOptionType>): Map<String, Map<String, Path>> =
        if (ServerCodeGenOptionType.GROUP_BY_TAG in options) {
            openApi3.routeToPathsByFirstTag()
        } else {
            openApi3.groupByPathSegment()
        }
}
