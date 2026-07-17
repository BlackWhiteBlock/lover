package com.lover.app.core.notice

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NoticeKind {
    Success,
    Error,
    Info,
}

data class AppNotice(
    val id: Long,
    val message: String,
    val kind: NoticeKind,
)

@Singleton
class NoticeStore @Inject constructor() {
    private val _notice = MutableStateFlow<AppNotice?>(null)
    val notice = _notice.asStateFlow()

    private val queue = ArrayDeque<AppNotice>()

    fun show(message: String, kind: NoticeKind = NoticeKind.Info) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        val next = AppNotice(
            id = System.nanoTime(),
            message = trimmed,
            kind = kind,
        )
        synchronized(queue) {
            if (_notice.value == null && queue.isEmpty()) {
                _notice.value = next
            } else {
                queue.addLast(next)
            }
        }
    }

    fun success(message: String) = show(message, NoticeKind.Success)
    fun error(message: String) = show(message, NoticeKind.Error)
    fun info(message: String) = show(message, NoticeKind.Info)

    /** Dismiss current; show next queued notice if any. */
    fun clear() {
        synchronized(queue) {
            _notice.value = queue.removeFirstOrNull()
        }
    }

    fun clearAll() {
        synchronized(queue) {
            queue.clear()
            _notice.value = null
        }
    }
}
