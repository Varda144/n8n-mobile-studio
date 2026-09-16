package com.n8n.mobile.studio.runtime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
class RuntimeLock{ private val m=Mutex(); suspend fun<T> withLock(b:suspend()->T):T=m.withLock{ b() } }
