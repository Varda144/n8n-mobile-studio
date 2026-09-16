package com.n8n.mobile.studio.runtime.opencode
data class OpenCodeVersion(val major:Int,val minor:Int,val patch:Int):Comparable<OpenCodeVersion>{
    override fun compareTo(other:OpenCodeVersion):Int = compareValuesBy(this,other,{it.major},{it.minor},{it.patch})
    companion object{ val CURRENT=OpenCodeVersion(0,1,0)}
}
