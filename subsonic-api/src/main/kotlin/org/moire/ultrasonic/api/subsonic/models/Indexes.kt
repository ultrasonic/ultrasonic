package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Indexes(val lastModified: Long = 0,
                   val ignoredArticles: String = "",
                   @JsonProperty("index")
                   val indexList: List<Index> = emptyList(),
                   val shortcuts: List<Index> = emptyList())
