package com.n8n.mobile.studio.core
sealed class AppResult<out T> {
    data class Success<T>(val data:T):AppResult<T>()
    data class Error(val throwable:Throwable):AppResult<Nothing>()
    object Loading:AppResult<Nothing>()
    val isSuccess get()=this is Success
    val isError get()=this is Error
    fun getOrNull():T?=(this as? Success)?.data
    fun exceptionOrNull():Throwable?=(this as? Error)?.throwable
}
