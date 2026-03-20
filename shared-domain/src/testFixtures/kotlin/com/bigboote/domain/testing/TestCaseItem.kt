package com.bigboote.domain.testing

abstract class TestCaseItem<S : Any> {
    abstract val subject: S
    abstract val extra: String?

    open fun caseName(): String {
        val className = subject::class.simpleName ?: "<anonymous>"
        return if (extra == null) className
        else "$className ($extra)"
    }
}