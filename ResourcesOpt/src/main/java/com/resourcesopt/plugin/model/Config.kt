package com.resourcesopt.plugin.model

import java.io.Serializable

data class Config(
    var repeatResOptEnable: Boolean = true,
    var resNameOptEnable: Boolean = true,
    var resNameOptWhiteRegexList: List<String> = emptyList(),
    var resNameOptPlaceholder: String = "opt",
): Serializable