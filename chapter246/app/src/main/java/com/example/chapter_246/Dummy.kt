package com.example.chapter_246

import androidx.annotation.DrawableRes

data class Lib(@DrawableRes val icon: Int, val name: String)

val libraries = listOf(
    Lib(R.drawable.ic_playlist, "Playlist"),
    Lib(R.drawable.ic_artist, "Artist"),
    Lib(R.drawable.ic_album, "Album"),
    Lib(R.drawable.ic_songs, "Songs"),
    Lib(R.drawable.ic_genre, "Genre"),
)