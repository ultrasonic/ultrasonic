package org.moire.ultrasonic.api.subsonic.models

import com.fasterxml.jackson.annotation.JsonProperty

data class Indexes(val lastModified: Long,
                   val ignoredArticles: String?,
                   @JsonProperty("index")
                   val indexList: List<Index>,
                   val shortcuts: List<Index>?)
