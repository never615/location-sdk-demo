package com.mallto.beacon

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mallto.beacon.databinding.ActivitySelectDomainBinding

val presetDomains = setOf(
    "https://test-easy.mall-to.com",
    "https://integration-easy.mall-to.com",
    "https://location-kwhrtls-dev.tstcld61.server.ha.org.hk",
    "https://location-kccnonc-kwh-rtls-prd.cldkwhprd1.server.ha.org.hk"
)

class SelectDomainActivity : AppCompatActivity() {
    private val binding by lazy { ActivitySelectDomainBinding.inflate(layoutInflater)}
    private val adapter = DomainAdapter()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        val userDomains = getSharedPreferences("app", 0).getStringSet("domain_list", emptySet())
        val list = mutableListOf<String>()
        list.addAll(presetDomains)
        list.addAll(userDomains!!)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.apply {
            btnAdd.setOnClickListener {
                val text = et.text.toString()
                if (text.isNotBlank()) {
                    getSharedPreferences("app", 0).edit {
                        val newSet = userDomains.toMutableSet()
                        newSet.add(text)
                        putStringSet("domain", newSet)
                    }
                    val newList = mutableListOf<String>()
                    newList.addAll(list)
                    newList.add(text)
                    adapter.submitList(newList)
                }
            }
            rv.layoutManager = LinearLayoutManager(this@SelectDomainActivity)
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
            holder.itemView.setOnClickListener {

                holder.itemView.context.getSharedPreferences("app", 0).edit {
                    putString("domain", data)
                }
                setResult(RESULT_OK, Intent().apply {
                    putExtra("domain", data)
                })
                finish()
            }
        }

    }
}
