package com.n8n.mobile.studio.terminal
data class TerminalOutput(val output:String, val isError:Boolean=false, val exitCode:Int?=null, val ts:Long=System.currentTimeMillis()){
    companion object{
        fun success(o:String,c:Int=0)=TerminalOutput(o,false,c)
        fun error(o:String,c:Int=-1)=TerminalOutput(o,true,c)
        fun partial(o:String)=TerminalOutput(o,false,null)
    }
}
