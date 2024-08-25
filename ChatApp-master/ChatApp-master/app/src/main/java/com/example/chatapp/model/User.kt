package com.example.chatapp.model

import com.google.firebase.firestore.Exclude

data class User(
    val name: String,
    val profileImage: String,
  @Exclude  val id: String
) {
    constructor() : this("", "", "")
}