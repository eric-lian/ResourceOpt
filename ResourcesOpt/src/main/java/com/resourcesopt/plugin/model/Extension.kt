package com.resourcesopt.plugin.model

abstract class Extension(
    var repeatResOptEnable: Boolean = true,
    var resNameOptEnable: Boolean = true,
    var resNameOptWhiteRegexList: List<String> = emptyList(),
    var resNameOptPlaceholder: String = "opt",
) {
    fun toConfig(): Config {
        return Config(
            repeatResOptEnable,
            resNameOptEnable,
            resNameOptWhiteRegexList,
            resNameOptPlaceholder
        )
    }
}