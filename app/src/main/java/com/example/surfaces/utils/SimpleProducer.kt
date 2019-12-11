package com.example.surfaces.utils

class SimpleProducer<T : Any>(private val produceCallback: () -> T?) {
    private var consumeCallback: ((T) -> Unit)? = null

    init {
        putResult()
    }

    fun consume(consumeCallback: (T) -> Unit) {
        this.consumeCallback = consumeCallback
        produceCallback.invoke()?.let { result ->
            notifyConsumer(result)
        }
    }

    fun putResult(result: T? = null) {
        if (result != null) {
            notifyConsumer(result)
        } else {
            produceCallback.invoke()?.let {
                notifyConsumer(it)
            }
        }
    }

    private fun notifyConsumer(res: T) {
        this.consumeCallback?.invoke(res)
        this.consumeCallback = null
    }
}