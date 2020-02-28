package com.zyyoona7.adjustablelayoutmanager

import com.chad.library.adapter.base.BaseDelegateMultiAdapter
import com.chad.library.adapter.base.delegate.BaseMultiTypeDelegate
import com.chad.library.adapter.base.viewholder.BaseViewHolder

class MyAdapter: BaseDelegateMultiAdapter<Item, BaseViewHolder>() {

    init {

        setMultiTypeDelegate(object :BaseMultiTypeDelegate<Item>(){
            override fun getItemType(data: List<Item>, position: Int): Int {
                return data[position].type
            }
        })

        getMultiTypeDelegate()?.addItemType(1,R.layout.item_type_1)
        getMultiTypeDelegate()?.addItemType(2,R.layout.item_type_2)
    }

    override fun convert(helper: BaseViewHolder, item: Item) {

    }
}

class Item(val type:Int)