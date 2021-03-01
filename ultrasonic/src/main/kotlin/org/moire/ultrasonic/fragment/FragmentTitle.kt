package org.moire.ultrasonic.fragment

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

/**
 * Contains utility functions related to Fragment title handling
 */
class FragmentTitle {
    companion object {
        fun setTitle(fragment: Fragment, title: CharSequence?) {
            (fragment.activity as AppCompatActivity).supportActionBar?.title = title
        }

        fun setTitle(fragment: Fragment, id: Int) {
            (fragment.activity as AppCompatActivity).supportActionBar?.setTitle(id)
        }

        fun getTitle(fragment: Fragment): CharSequence? {
            return (fragment.activity as AppCompatActivity).supportActionBar?.title
        }
    }
}
