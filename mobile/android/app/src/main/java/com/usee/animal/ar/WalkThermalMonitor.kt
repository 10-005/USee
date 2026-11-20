package com.usee.animal.ar

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/** All intervals stay below the 1.5s semantic expiry; motion wakes inference immediately. */
internal fun analysisIntervalNanos(active: Boolean, reducedWorkload: Boolean): Long = when {
  active && !reducedWorkload -> 250_000_000L
  active -> 500_000_000L
  !reducedWorkload -> 750_000_000L
  else -> 1_000_000_000L
}

internal class ThermalWorkloadPolicy {
  @Volatile var reduced = false
    private set
  fun update(status: Int, headroom: Float = Float.NaN) {
    if (status >= 2 || headroom.isFinite() && headroom >= 0.85f) reduced = true
    else if (status == 0 && (!headroom.isFinite() || headroom <= 0.7f)) reduced = false
  }
}

internal class WalkThermalMonitor(context: Context) {
  private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
  private var listening = false
  private val policy = ThermalWorkloadPolicy()
  private var status = 0
  private var headroom = Float.NaN
  private var lastHeadroomRead = 0L
  val reducedWorkload: Boolean get() {
    val now = SystemClock.elapsedRealtime()
    // Some devices expose headroom while reporting an uninformative NONE status.
    if (listening && Build.VERSION.SDK_INT >= 30 && (lastHeadroomRead == 0L || now - lastHeadroomRead >= 10_000L)) {
      lastHeadroomRead = now
      headroom = power.getThermalHeadroom(0)
      policy.update(status, headroom)
    }
    return policy.reduced
  }
  private val listener = if (Build.VERSION.SDK_INT >= 29) PowerManager.OnThermalStatusChangedListener { status ->
    // Restore only after cooling below LIGHT to avoid oscillating quality levels.
    this.status = status
    policy.update(status, headroom)
    Log.i("ArWalkThermal", "status=$status headroom=$headroom reducedWorkload=${policy.reduced}")
  } else null

  fun start() {
    if (Build.VERSION.SDK_INT < 29 || listening) return
    status = power.currentThermalStatus
    policy.update(status)
    power.addThermalStatusListener(listener!!)
    listening = true
  }
  fun stop() {
    if (Build.VERSION.SDK_INT >= 29 && listening) power.removeThermalStatusListener(listener!!)
    listening = false
    status = 0
    headroom = Float.NaN
    lastHeadroomRead = 0L
    policy.update(0)
  }
}
