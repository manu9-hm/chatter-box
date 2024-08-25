package com.example.chatapp.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date


class ChatMessage(
    val sender: User,
    val message: String,
    @ServerTimestamp val timeStamp: Date?,
    val image: String
) {
    constructor() : this(User(), "", null,"")

    constructor(image: String): this(User(),"",null,image)
    constructor(user: User, image: String): this(user,"",null,image)
}