package com.n8n.mobile.studio.runtime.n8n
data class N8nVersion(val major:Int,val minor:Int,val patch:Int):Comparable<N8nVersion>{
    override fun compareTo(other:N8nVersion):Int = compareValuesBy(this,other,{it.major},{it.minor},{it.patch})
    companion object{ fun parse(s:String):N8nVersion{ val p=s.removePrefix("v").split("."); return N8nVersion(p.getOrElse(0){"0"}.toIntOrNull()?:0,p.getOrElse(1){"0"}.toIntOrNull()?:0,p.getOrElse(2){"0"}.toIntOrNull()?:0)}; val CURRENT=N8nVersion(1,28,0)}
}
