package org.moire.ultrasonic.fragment

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class FragmentTitle {
    companion object {
        fun setTitle(fragment: Fragment, title: CharSequence?) {
            (fragment.activity as AppCompatActivity).supportActionBar?.setTitle(title)
        }

        fun setTitle(fragment: Fragment, id: Int) {
            (fragment.activity as AppCompatActivity).supportActionBar?.setTitle(id)
        }

        fun getTitle(fragment: Fragment): CharSequence? {
            return (fragment.activity as AppCompatActivity).supportActionBar?.title
        }

        fun setSubtitle(fragment: Fragment, title: CharSequence?) {
            (fragment.activity as AppCompatActivity).supportActionBar?.setSubtitle(title)
        }

        fun setSubtitle(fragment: Fragment, id: Int) {
            (fragment.activity as AppCompatActivity).supportActionBar?.setSubtitle(id)
        }

        fun getSubtitle(fragment: Fragment): CharSequence? {
            return (fragment.activity as AppCompatActivity).supportActionBar?.subtitle
        }
    }
}