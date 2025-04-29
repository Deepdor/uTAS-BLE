// File: TagInfo.kt
package com.example.minimalnfc

data class TagInfo(
    val uid: String,
    val name: String = "",
    val surname: String = "",
    val tagType: String = "",
    val photo: String? = null // Resource name for the photo (optional)
)
