package com.mallto.beacon

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mallto.beacon.databinding.ActivitySelectDomainBinding
import com.mallto.beacon.databinding.ActivityUuidListBinding


class UuidListActivity : AppCompatActivity() {
    private val binding by lazy { ActivityUuidListBinding.inflate(layoutInflater)}
    private val adapter = DomainAdapter()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        val uuidList = getSharedPreferences("app", 0).getStringSet("uuid_list", emptySet())
        val list = mutableListOf<String>()
        list.addAll(uuidList!!)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.apply {

            et.setText("FDA50693-A4E2-4FB1-AFCF-C6EB07647827")
            btnAdd.setOnClickListener {
                val text = et.text.toString()
                if (text.isNotBlank()) {
                    getSharedPreferences("app", 0).edit {
                        val newSet = uuidList.toMutableSet()
                        newSet.add(text)
                        putStringSet("uuid_list", newSet)
                    }
                    val newList = mutableListOf<String>()
                    newList.addAll(list)
                    newList.add(text)
                    adapter.submitList(newList)
                }
            }
            rv.layoutManager = LinearLayoutManager(this@UuidListActivity)
            rv.adapter = adapter
            adapter.submitList(list)
        }

    }
    class DomainViewHolder(view: TextView): RecyclerView.ViewHolder(view) {

    }

    class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return true
        }

    }

    inner class DomainAdapter() : ListAdapter<String, DomainViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DomainViewHolder {

            return DomainViewHolder(TextView(parent.context).apply {
                setBackgroundColor(Color.GRAY)
                setTextColor(Color.BLACK)
                setPadding(24)
            })
        }

        override fun onBindViewHolder(holder: DomainViewHolder, position: Int) {
            val data = currentList[position]
            (holder.itemView as TextView).text = data
        }

    }
}