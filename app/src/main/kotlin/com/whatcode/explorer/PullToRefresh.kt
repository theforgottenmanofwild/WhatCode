package com.whatcode.explorer

import android.app.Activity
import android.view.MotionEvent
import android.widget.AbsListView

/**
 * Pull to refresh for the explorer's list, without taking any touch away from it: it only watches. A drag down that
 * starts while the list is at its top goes through [State.PULLING] to [State.READY] once it is long enough, and
 * letting go then calls [onState] with [State.REFRESHING]; [done] puts it back to [State.IDLE].
 */
internal class PullToRefresh(activity: Activity, private val onState: (State) -> Unit) {

    enum class State { IDLE, PULLING, READY, REFRESHING }

    private val density = activity.resources.displayMetrics.density
    private val start = 12 * density
    private val far = 80 * density

    private var downY = -1f
    private var state = State.IDLE

    private fun set(value: State) {
        if (value == state) return
        state = value
        onState(value)
    }

    fun onTouch(list: AbsListView, event: MotionEvent) {
        if (state == State.REFRESHING) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> downY = if (list.canScrollVertically(-1)) -1f else event.y
            MotionEvent.ACTION_MOVE -> {
                if (downY < 0) return
                if (list.canScrollVertically(-1)) {
                    downY = -1f
                    set(State.IDLE)
                    return
                }
                val pulled = event.y - downY
                set(
                    when {
                        pulled >= far -> State.READY
                        pulled >= start -> State.PULLING
                        else -> State.IDLE
                    }
                )
            }
            MotionEvent.ACTION_UP -> {
                val ready = state == State.READY
                downY = -1f
                set(if (ready) State.REFRESHING else State.IDLE)
            }
            MotionEvent.ACTION_CANCEL -> {
                downY = -1f
                set(State.IDLE)
            }
        }
    }

    /** The folder was read again (or something else read it): the line at the top goes away. */
    fun done() = set(State.IDLE)
}
