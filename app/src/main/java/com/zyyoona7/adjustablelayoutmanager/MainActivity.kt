package com.zyyoona7.adjustablelayoutmanager

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.AdjustableLayoutManager
import com.zyyoona7.itemdecoration.RecyclerViewDivider
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val adapter=MyAdapter()
        recyclerview.adapter=adapter
        val layoutManager= AdjustableLayoutManager(this)
        layoutManager.setAdjustableItemType(1)
        layoutManager.setMinAdjustableItemRatio(1f)
        recyclerview.layoutManager=layoutManager

        RecyclerViewDivider.linear()
            .dividerSize(5)
            .asSpace()
            .build()
            .addTo(recyclerview)

        adapter.setNewData(arrayListOf(Item(1),Item(2)))

        btn_add1.setOnClickListener {
            adapter.addData(Item(1))
        }

        btn_add2.setOnClickListener {
            adapter.addData(Item(2))
        }

        btn_del1.setOnClickListener {
            if (adapter.data.size>0){
                adapter.remove(0)
            }
//            layoutManager.setAdjustableItemType(1)
        }
        btn_del2.setOnClickListener {
            if(adapter.data.size>0){
                adapter.remove(adapter.data.size-1)
            }
//            layoutManager.setAdjustableItemType(2)
        }
    }
}
