package com.example.chatapp.ui


import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.MainActivity
import com.example.chatapp.R
import com.example.chatapp.adaptors.MessagesAdaptor
import com.example.chatapp.model.ChatMessage
import com.example.chatapp.model.User
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference


class ChatActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val usersRef: CollectionReference = db.collection("users_collection")
    private val messagesRef: CollectionReference = db.collection("messages_collection")
    private lateinit var sendButton: Button
    private lateinit var editTextMessage: EditText
    private lateinit var messagesAdaptor: MessagesAdaptor
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messages: MutableList<ChatMessage>
    private lateinit var currentUser: User
    private lateinit var storageRef: StorageReference
    private lateinit var uri: Uri
    private lateinit var getResult: ActivityResultLauncher<Intent>
    private val STORAGE_REQUEST_CODE = 23231
    private lateinit var progressBar: ProgressBar

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        messagesRecyclerView = findViewById(R.id.message_recycler_view)
        sendButton = findViewById(R.id.send_message_button)
        editTextMessage = findViewById(R.id.input_message)
        progressBar = findViewById(R.id.progressBarChatAct)
        storageRef = FirebaseStorage.getInstance().reference
        initRecyclerView()
        getCurrentUser()

        sendButton.setOnClickListener { insertMessage() }


        editTextMessage.setOnTouchListener { _, event ->
            val DRAWABLE_RIGHT = 2
            val DRAWABLE_LEFT = 0
            val DRAWABLE_TOP = 1
            val DRAWABLE_BOTTOM = 3
            if (event.action == MotionEvent.ACTION_UP) {
                if (event.rawX >= (editTextMessage.right - editTextMessage.compoundDrawables[DRAWABLE_RIGHT].bounds.width())) {
                    editTextMessage.setText("")
                    if (ActivityCompat.checkSelfPermission(
                            this@ChatActivity,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermission()
                    } else {
                        getImage()
                    }
                }
            }
            false
        }
        getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                uri = it.data?.data!!
                hideProgressBar()
            }
        }

    }

    override fun onStart() {
        super.onStart()

        messagesRef.orderBy("timeStamp", Query.Direction.ASCENDING)
            .addSnapshotListener(this) { snapshots, error ->
                error?.let {
                    return@addSnapshotListener
                }

                snapshots?.let {
                    for (dc in it.documentChanges) {
                        val oldIndex = dc.oldIndex
                        val newIndex = dc.newIndex

                        when (dc.type) {
                            DocumentChange.Type.ADDED -> {
                                val snapshot = dc.document
                                val message = snapshot.toObject(ChatMessage::class.java)
                                messages.add(newIndex, message)
                                messagesAdaptor.notifyItemInserted(newIndex)
                                messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                                Toast.makeText(this, message.image, Toast.LENGTH_LONG).show()
                            }
                            DocumentChange.Type.REMOVED -> {

                            }
                            DocumentChange.Type.MODIFIED -> {

                            }
                        }

                    }
                }
            }
    }

    private fun initRecyclerView() {
        messages = mutableListOf()
        messagesAdaptor = MessagesAdaptor(this@ChatActivity, messages)
        messagesRecyclerView.setAdapter(messagesAdaptor)
        messagesRecyclerView.setHasFixedSize(true)
        messagesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun getCurrentUser() {
        usersRef.whereEqualTo("id", FirebaseAuth.getInstance().currentUser?.uid)
            .get()
            .addOnSuccessListener {
                for (snapshot in it) {
                    currentUser = snapshot.toObject(User::class.java)
                }
            }
    }

    private fun insertMessage() {
        var message = editTextMessage.text.toString()


        //  current user was null !!!!!!
        if (message.isNotEmpty()) {
            messagesRef.document()
                .set(ChatMessage(currentUser, message, null, ""))
                .addOnCompleteListener {
                    if (it.isComplete) {
                        editTextMessage.setText("")
                        message = ""
                    } else {

                    }
                }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_sing_out -> {
                FirebaseAuth.getInstance().signOut()
                Intent(this@ChatActivity, MainActivity::class.java).also {
                    startActivity(it)
                }
                return true
            }
        }
        return false
    }

    private fun requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this@ChatActivity,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            AlertDialog.Builder(this@ChatActivity)
                .setPositiveButton(R.string.dialog_button_yes) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this@ChatActivity,
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                        STORAGE_REQUEST_CODE
                    )
                }.setNegativeButton(R.string.dialog_button_no) { dialog, _ ->
                    dialog.cancel()
                }.setTitle("Permission needed")
                .setMessage("This permission is needed for accessing the internal storage")
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this@ChatActivity, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_REQUEST_CODE
            )
        }
    }

    private fun getImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        getResult.launch(intent)

        uploadImage()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_REQUEST_CODE && grantResults.size > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getImage()
        } else {
            Toast.makeText(this@ChatActivity, "Permission not granted", Toast.LENGTH_LONG).show()
        }
    }

    private fun uploadImage() {
        if (this::uri.isInitialized) {
            showProgressBar()
            val filePath = storageRef.child("chat_images").child(uri.lastPathSegment!!)
            filePath.putFile(uri).addOnSuccessListener { task ->
                val result: Task<Uri> = task.metadata?.reference?.downloadUrl!!
                result.addOnSuccessListener {
                    uri = it
                    Toast.makeText(this@ChatActivity, it.toString(), Toast.LENGTH_SHORT).show()

                }
                val message = ChatMessage(currentUser, uri.toString())
                messagesRef.document()
                    .set(message)
                    .addOnCompleteListener {
                        if (it.isComplete) {
                            Toast.makeText(this,"Image added!",Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this,"Image wasn't added!",Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }


    private fun showProgressBar() {
        progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        progressBar.visibility = View.GONE
    }
}

