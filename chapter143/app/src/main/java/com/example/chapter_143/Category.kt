package com.example.chapter_143

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// TODO: [todos/android-parcelable-vs-serializable.md](../../../../../../../../todos/android-parcelable-vs-serializable.md)
@Parcelize
data class Category(
    val idCategory: String,
    val strCategory: String,
    val strCategoryThumb: String,
    val strCategoryDescription: String
) : Parcelable

data class CategoriesResponse(
    val categories: List<Category>
)