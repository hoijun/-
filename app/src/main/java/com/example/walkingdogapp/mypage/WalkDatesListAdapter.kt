package com.example.walkingdogapp.mypage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.walkingdogapp.databinding.WalkdateslistItemBinding
import com.example.walkingdogapp.datamodel.WalkRecord

class WalkDatesListAdapter(private val walkRecordList: List<WalkRecord>): RecyclerView.Adapter<WalkDatesListAdapter.WalkDatesListViewHolder>() {

    fun interface OnItemClickListener {
        fun onItemClick(date: WalkRecord)
    }

    var itemClickListener : OnItemClickListener? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalkDatesListViewHolder {
        val binding = WalkdateslistItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WalkDatesListViewHolder(binding)
    }

    override fun getItemCount(): Int = walkRecordList.size

    override fun onBindViewHolder(holder: WalkDatesListViewHolder, position: Int) {
        holder.bind(walkRecordList[position])
    }

    inner class WalkDatesListViewHolder(private val binding: WalkdateslistItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                itemClickListener?.onItemClick(walkRecordList[bindingAdapterPosition])
            }
        }
        fun bind(walkRecord: WalkRecord) {
            binding.apply {
                walkRecordInfo = walkRecord
            }
        }
    }
}
