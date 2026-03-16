package com.mallto.beacon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mallto.beacon.databinding.ActivityUuidListBinding

class UuidListActivity : AppCompatActivity() {
    private val binding by lazy { ActivityUuidListBinding.inflate(layoutInflater) }
    private val adapter = UuidAdapter { uuid -> removeUuid(uuid) }
    private val uuidList = mutableListOf<String>()

    companion object {
        val COMMON_UUIDS = listOf(
            "FDA50693-A4E2-4FB1-AFCF-C6EB07647827"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        loadUuidList()

        binding.rvUuidList.layoutManager = LinearLayoutManager(this)
        binding.rvUuidList.adapter = adapter

        binding.btnAdd.setOnClickListener {
            val text = binding.etUuid.text.toString().trim()
            if (text.isBlank()) {
                Toast.makeText(this, "请输入 UUID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (uuidList.contains(text)) {
                Toast.makeText(this, "该 UUID 已存在", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            uuidList.add(text)
            saveUuidList()
            refreshList()
            binding.etUuid.text?.clear()
            Toast.makeText(this, "添加成功", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddCommon.setOnClickListener {
            showCommonUuidSheet()
        }

        refreshList()
    }

    private fun loadUuidList() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        val set = prefs.getStringSet("uuid_list", emptySet()) ?: emptySet()
        uuidList.clear()
        uuidList.addAll(set)
    }

    private fun saveUuidList() {
        getSharedPreferences("app", MODE_PRIVATE).edit {
            putStringSet("uuid_list", uuidList.toSet())
        }
    }

    private fun removeUuid(uuid: String) {
        uuidList.remove(uuid)
        saveUuidList()
        refreshList()
        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
    }

    private fun showCommonUuidSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_common_uuid, null)
        val rv = view.findViewById<RecyclerView>(R.id.rvCommonUuid)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = CommonUuidAdapter(COMMON_UUIDS) { uuid ->
            binding.etUuid.setText(uuid)
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun refreshList() {
        adapter.submitList(uuidList.toList())
        if (uuidList.isEmpty()) {
            binding.rvUuidList.visibility = View.GONE
            binding.tvEmptyHint.visibility = View.VISIBLE
        } else {
            binding.rvUuidList.visibility = View.VISIBLE
            binding.tvEmptyHint.visibility = View.GONE
        }
    }

    class UuidViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUuid: TextView = view.findViewById(R.id.tvUuid)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    class UuidAdapter(private val onDelete: (String) -> Unit) :
        ListAdapter<String, UuidViewHolder>(object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        }) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UuidViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_uuid, parent, false)
            return UuidViewHolder(view)
        }

        override fun onBindViewHolder(holder: UuidViewHolder, position: Int) {
            val uuid = getItem(position)
            holder.tvUuid.text = uuid
            holder.btnDelete.setOnClickListener { onDelete(uuid) }
        }
    }

    class CommonUuidAdapter(
        private val items: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<CommonUuidAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvUuid: TextView = view.findViewById(R.id.tvCommonUuid)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_common_uuid, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uuid = items[position]
            holder.tvUuid.text = uuid
            holder.itemView.setOnClickListener { onClick(uuid) }
        }

        override fun getItemCount() = items.size
    }
}
