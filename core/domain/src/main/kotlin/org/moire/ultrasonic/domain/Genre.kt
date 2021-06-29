package org.moire.ultrasonic.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity
data class Genre(
    @PrimaryKey val index: String,
    override val name: String
) : Serializable, GenericEntry() {
    companion object {
        private const val serialVersionUID = -3943025175219134028L
    }
}
