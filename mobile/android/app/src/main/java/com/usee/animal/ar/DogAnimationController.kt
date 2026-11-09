package com.usee.animal.ar

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

data class DogAnimation(
  val clip: String = "Idle",
  val loop: Boolean = true,
  val revision: Int = 0,
  val playbackRate: Float = 1f,
)

class DogAnimationController(
  private val durations: Map<String, Float> = emptyMap(),
  private val choose: (Int) -> Int = { Random.nextInt(it) },
) {
  private data class Step(val clip: String, val seconds: Float, val loop: Boolean = false)
  var current = DogAnimation()
    private set
  private var stoppedSeconds = 0f
  private var shortIdlePlayed = false
  private var longIdlePlayed = false
  private var requested: String? = null
  private var steps: List<Step> = emptyList()
  private var stepIndex = 0
  private var stepSeconds = 0f
  private var candidate = "Idle"
  private var candidateSeconds = 0f
  private var heldSitSeconds: Float? = null

  /** Voice sitting persists until an explicit go/stop command, without random idle actions. */
  fun holdPosition(sitting: Boolean, dt: Float) {
    requested = null
    steps = emptyList()
    if (!sitting) {
      heldSitSeconds = null
      change("Idle", true)
      return
    }
    val elapsed = heldSitSeconds
    if (elapsed == null) {
      heldSitSeconds = 0f
      change("SitStart", false, force = true)
    } else {
      heldSitSeconds = elapsed + dt.coerceAtLeast(0f)
      if (heldSitSeconds!! >= (durations["SitStart"] ?: 1f)) change("SitIdle", true)
    }
  }

  fun request(action: String) {
    if (action in ACTIONS) requested = action
  }

  internal fun carePose(clip: String, playbackRate: Float = 1f) =
    change(clip, loop = false, playbackRate = playbackRate)

  internal fun clipSeconds(clip: String) = durations[clip] ?: 1f

  internal fun beginConsumption(action: String) = start(plan(action))

  internal fun advanceConsumption(dt: Float): Boolean {
    advanceSteps(dt)
    return steps.isNotEmpty()
  }

  fun holdSpin() {
    requested = null
    steps = emptyList()
    heldSitSeconds = null
    change(turnClip(1f, halfTurn = false), true)
  }

  fun pause() {
    heldSitSeconds = null
    requested = null
    steps = emptyList()
    stoppedSeconds = 0f
    shortIdlePlayed = false
    longIdlePlayed = false
    change("Idle", true)
  }

  fun update(
    dt: Float,
    userSpeed: Float,
    dogSpeed: Float,
    forwardSpeed: Float,
    rightSpeed: Float,
    turnRate: Float,
    turnRemaining: Float,
  ): DogAnimation {
    if (dt <= 0f) return current
    val moving = dogSpeed > 0.07f
    val turning = abs(turnRate) > 0.4f && abs(turnRemaining) > 0.12f
    if (moving || turning || userSpeed > 0.16f) {
      stoppedSeconds = 0f
      shortIdlePlayed = false
      longIdlePlayed = false
      steps = emptyList()
      requested = null
      val clip = when {
        moving -> movementClip(dogSpeed, forwardSpeed, rightSpeed)
        turning -> turnClip(turnRate, halfTurn = abs(turnRemaining) > 1.8f)
        else -> "Idle"
      }
      settleMotion(clip, dt)
      return current
    }

    stoppedSeconds += dt
    requested?.let { action ->
      requested = null
      stoppedSeconds = 0f
      shortIdlePlayed = true
      longIdlePlayed = false
      start(plan(action))
      return current
    }
    if (stoppedSeconds >= 10f && !longIdlePlayed) {
      longIdlePlayed = true
      start(plan(if (choose(2) == 0) "crouch" else "dig"))
      return current
    }
    if (steps.isNotEmpty()) {
      advanceSteps(dt)
      return current
    }
    if (stoppedSeconds >= 3f && !shortIdlePlayed) {
      shortIdlePlayed = true
      val idle = IDLE_CLIPS[choose(IDLE_CLIPS.size)]
      start(if (idle == "Idle5") {
        listOf(once("Idle5Start"), hold("Idle5Loop", 3f), once("Idle5End"))
      } else listOf(once(idle)))
    } else {
      settleMotion("Idle", dt)
    }
    return current
  }

  private fun advanceSteps(dt: Float) {
    stepSeconds += dt
    while (steps.isNotEmpty() && stepSeconds >= steps[stepIndex].seconds) {
      stepSeconds -= steps[stepIndex].seconds
      stepIndex++
      if (stepIndex == steps.size) {
        steps = emptyList()
        change("Idle", true)
      } else {
        val step = steps[stepIndex]
        change(step.clip, step.loop, force = true)
      }
    }
  }

  private fun movementClip(speed: Float, forward: Float, right: Float): String {
    val angle = Math.toDegrees(atan2(right, forward).toDouble()).toFloat()
    val magnitude = abs(angle)
    val side = if (angle > 0f) "Right" else "Left"
    if (magnitude > 157.5f) return "WalkBack"
    if (magnitude > 112.5f) return "WalkBack$side"
    val prefix = when {
      speed > 3f -> "RunFast"
      speed > 2.3f || (current.clip.startsWith("Gallop") && speed > 1.7f) -> "Gallop"
      speed > 1.2f || (current.clip.startsWith("Trot") && speed > 0.95f) -> "Trot"
      else -> "Walk"
    }
    return prefix + if (magnitude > 22.5f) side else ""
  }

  private fun settleMotion(clip: String, dt: Float) {
    if (candidate != clip) {
      candidate = clip
      candidateSeconds = 0f
    }
    candidateSeconds += dt
    if (!isLocomotion(current.clip) && clip != "Idle" || candidateSeconds >= 0.1f) {
      change(clip, true)
    }
  }

  private fun once(clip: String) = Step(clip, durations[clip] ?: 1f)
  private fun hold(clip: String, seconds: Float = 4f) = Step(clip, seconds, true)

  private fun plan(action: String): List<Step> = when (action) {
    "bark" -> listOf(once("Bark"))
    "sit" -> listOf(once("SitStart"), hold(if (choose(2) == 0) "SitIdle" else "SitIdle2"), once("SitEnd"))
    "lie" -> listOf(once("LieStart"), hold(if (choose(2) == 0) "LieIdle" else "LieIdle2"), once("LieEnd"))
    "bellyLie" -> listOf(once("BellyLieStart"), hold(if (choose(2) == 0) "BellyLieIdle" else "BellyLieIdle2"), once("BellyLieEnd"))
    "jumpPlace" -> listOf(once("JumpPlace"))
    "jumpForward" -> listOf(once("JumpForward"))
    "jumpRun" -> listOf(once("JumpRun"))
    "jumpHigh" -> listOf(once("JumpStartUp"), Step("JumpAirUp", 0.3f), Step("JumpAirHigh", 0.25f), Step("JumpAirLow", 0.3f), once("JumpLand"))
    "eat" -> listOf(once("EatDrinkStart"), hold(if (choose(2) == 0) "Eat" else "EatTear"), once("EatDrinkEnd"))
    "drink" -> listOf(once("EatDrinkStart"), hold("Drink"), once("EatDrinkEnd"))
    "dig" -> listOf(once("DigStart"), hold("DigLoop"), once("DigEnd"))
    "crouch" -> listOf(once("CrouchStart"), hold(if (choose(2) == 0) "CrouchIdle" else "CrouchIdle2"), once("CrouchEnd"))
    "scratch" -> listOf(once("ScratchEar"))
    "pee" -> listOf(once("Pee"))
    "poop" -> listOf(once("Poop"))
    else -> emptyList()
  }

  private fun start(plan: List<Step>) {
    steps = plan
    stepIndex = 0
    stepSeconds = 0f
    candidateSeconds = 0f
    plan.firstOrNull()?.let { change(it.clip, it.loop, force = true) }
  }

  private fun change(clip: String, loop: Boolean, force: Boolean = false, playbackRate: Float = 1f) {
    if (force || current.clip != clip || current.loop != loop || current.playbackRate != playbackRate) {
      current = DogAnimation(clip, loop, current.revision + 1, playbackRate)
    }
  }

  companion object {
    // Positive world yaw turns the +Z-facing model toward its anatomical left.
    internal fun turnClip(yawDelta: Float, halfTurn: Boolean): String {
      val side = if (yawDelta > 0f) "TurnLeft" else "TurnRight"
      return if (halfTurn) "${side}180" else side
    }

    private val IDLE_CLIPS = listOf("Idle2", "Idle3", "Idle4", "Idle5", "Idle6", "Idle7", "ScratchEar")
    val ACTIONS = setOf("bark", "sit", "lie", "bellyLie", "jumpPlace", "jumpForward", "jumpRun", "jumpHigh", "eat", "drink", "dig", "crouch", "scratch", "pee", "poop")
    fun isLocomotion(clip: String) = clip.startsWith("Walk") || clip.startsWith("Trot") ||
      clip.startsWith("Gallop") || clip.startsWith("RunFast") || clip.startsWith("Turn")
  }
}
