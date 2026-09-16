package com.n8n.mobile.studio.runtime
enum class RuntimeState { IDLE, INSTALLING, STARTING, RUNNING, STOPPING, STOPPED, ERROR, RESTARTING;
    val isActive get()=this==RUNNING||this==STARTING
    val isTransitioning get()=this==INSTALLING||this==STARTING||this==STOPPING||this==RESTARTING
}
