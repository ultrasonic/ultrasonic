package org.moire.ultrasonic.filepicker

import java.io.File

interface OnFileSelectedListener {
    fun onFileSelected(file: File?, path: String?)
}
