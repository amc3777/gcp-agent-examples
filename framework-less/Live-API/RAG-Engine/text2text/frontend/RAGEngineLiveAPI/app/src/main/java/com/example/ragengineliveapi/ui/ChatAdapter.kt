package com.example.ragengineliveapi.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ragengineliveapi.R
import com.example.ragengineliveapi.data.Turn

class ChatAdapter(private var turns: List<Turn>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageTextView: TextView = view.findViewById(R.id.messageTextView)
        val roleTextView: TextView = view.findViewById(R.id.roleTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val turn = turns[position]
        holder.roleTextView.text = turn.role.capitalize()
        holder.messageTextView.text = turn.parts.joinToString("\n") { it.text }
    }

    override fun getItemCount() = turns.size

    fun updateData(newTurns: List<Turn>) {
        this.turns = newTurns
        notifyDataSetChanged()
    }
}