package com.example.ragengineliveapi

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ragengineliveapi.ui.ChatAdapter
import com.example.ragengineliveapi.viewmodel.ChatViewModel
import com.example.ragengineliveapi.data.Turn

class MainActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chatRecyclerView: RecyclerView = findViewById(R.id.chatRecyclerView)
        val messageEditText: EditText = findViewById(R.id.messageEditText)
        val sendButton: Button = findViewById(R.id.sendButton)

        chatAdapter = ChatAdapter(emptyList())
        chatRecyclerView.adapter = chatAdapter
        chatRecyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.chatHistory.observe(this) { history: List<Turn> ->
            chatAdapter.updateData(history)
            chatRecyclerView.scrollToPosition(history.size - 1)
        }

        viewModel.errorMessage.observe(this) { error ->
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }

        sendButton.setOnClickListener {
            val message = messageEditText.text.toString()
            if (message.isNotBlank()) {
                viewModel.sendMessage(message)
                messageEditText.text.clear()
            }
        }
    }
}