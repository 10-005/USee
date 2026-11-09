package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class DogAnimationControllerTest {
  private fun tick(controller: DogAnimationController, seconds: Float) {
    repeat((seconds / 0.02f).toInt()) {
      controller.update(0.02f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
  }

  @Test fun threeSecondStopPlaysOneRandomIdleOnlyOnce() {
    val controller = DogAnimationController(choose = { it - 1 })
    tick(controller, 2.9f)
    assertEquals("Idle", controller.current.clip)
    tick(controller, 0.14f)
    assertEquals("ScratchEar", controller.current.clip)
    assertFalse(controller.current.loop)
    tick(controller, 1.2f)
    assertEquals("Idle", controller.current.clip)
    val revision = controller.current.revision
    tick(controller, 4f)
    assertEquals(revision, controller.current.revision)
  }

  @Test fun tenSecondStopChoosesCrouchOrDigAndPlaysEntryLoopExit() {
    for ((choice, prefix) in listOf(0 to "Crouch", 1 to "Dig")) {
      val controller = DogAnimationController(choose = { choice % it })
      tick(controller, 9.9f)
      assertEquals("Idle", controller.current.clip)
      tick(controller, 0.16f)
      assertEquals("${prefix}Start", controller.current.clip)
      tick(controller, 1.1f)
      assertEquals(if (choice == 0) "CrouchIdle" else "DigLoop", controller.current.clip)
      assertTrue(controller.current.loop)
      tick(controller, 4f)
      assertEquals("${prefix}End", controller.current.clip)
      tick(controller, 1.1f)
      assertEquals("Idle", controller.current.clip)
      val revision = controller.current.revision
      tick(controller, 15f)
      assertEquals(revision, controller.current.revision)
    }
  }

  @Test fun walkingImmediatelyInterruptsEveryManualAction() {
    for (action in DogAnimationController.ACTIONS) {
      val controller = DogAnimationController(choose = { 0 })
      controller.request(action)
      tick(controller, 0.02f)
      assertNotEquals(action, "Idle", controller.current.clip)
      controller.update(0.02f, 0.8f, 0.8f, 0.8f, 0f, 0f, 0f)
      assertEquals(action, "Walk", controller.current.clip)
      assertTrue(controller.current.loop)
    }
  }

  @Test fun cameraMovementOrCatchUpResetsStopTimer() {
    val controller = DogAnimationController(choose = { 0 })
    tick(controller, 2.9f)
    controller.update(0.02f, 0f, 0.8f, 0.8f, 0f, 0f, 0f)
    tick(controller, 2.9f)
    assertEquals("Idle", controller.current.clip)
    controller.update(0.02f, 1f, 0f, 0f, 0f, 0f, 0f)
    tick(controller, 2.9f)
    assertEquals("Idle", controller.current.clip)
  }

  @Test fun blockedWalkingDoesNotStartIdleGestures() {
    val controller = DogAnimationController(choose = { 0 })
    repeat(1000) { controller.update(0.02f, 0.6f, 0f, 0f, 0f, 0f, 0f) }
    assertEquals("Idle", controller.current.clip)
  }

  @Test fun movementDirectionSelectsForwardBackwardSideAndDiagonalClips() {
    for ((forward, right, expected) in listOf(
      Triple(0.5f, 0f, "Walk"), Triple(-0.5f, 0f, "WalkBack"),
      Triple(0f, -0.5f, "WalkLeft"), Triple(0f, 0.5f, "WalkRight"),
      Triple(-0.4f, -0.4f, "WalkBackLeft"), Triple(-0.4f, 0.4f, "WalkBackRight"),
      Triple(0.4f, -0.4f, "WalkLeft"), Triple(0.4f, 0.4f, "WalkRight"),
    )) {
      val controller = DogAnimationController()
      controller.update(0.02f, 0.5f, 0.5f, forward, right, 0f, 0f)
      assertEquals(expected, controller.current.clip)
    }
  }

  @Test fun speedAndTurningChooseTrotRunAndTurnClips() {
    for ((speed, expected) in listOf(0.5f to "Walk", 1.5f to "Trot", 2.5f to "Gallop", 3.4f to "RunFast")) {
      val controller = DogAnimationController()
      controller.update(0.02f, speed, speed, speed, 0f, 0f, 0f)
      assertEquals(expected, controller.current.clip)
    }
    for ((rate, angle, expected) in listOf(
      Triple(-1f, -1f, "TurnRight"), Triple(1f, 1f, "TurnLeft"),
      Triple(-1f, -2f, "TurnRight180"), Triple(1f, 2f, "TurnLeft180"),
    )) {
      val controller = DogAnimationController()
      controller.update(0.02f, 0f, 0f, 0f, 0f, rate, angle)
      assertEquals(expected, controller.current.clip)
    }
  }

  @Test fun manualSequenceUsesActualClipDurationAndReturnsToIdle() {
    val controller = DogAnimationController(mapOf("EatDrinkStart" to 0.5f, "EatDrinkEnd" to 0.5f), choose = { 0 })
    controller.request("eat")
    tick(controller, 0.02f)
    assertEquals("EatDrinkStart", controller.current.clip)
    assertFalse(controller.current.loop)
    tick(controller, 0.6f)
    assertEquals("Eat", controller.current.clip)
    assertTrue(controller.current.loop)
    tick(controller, 4f)
    assertEquals("EatDrinkEnd", controller.current.clip)
    tick(controller, 0.6f)
    assertEquals("Idle", controller.current.clip)
  }

  @Test fun stableMotionDoesNotRestartAnimation() {
    val controller = DogAnimationController()
    controller.update(0.02f, 0.6f, 0.6f, 0.6f, 0f, 0f, 0f)
    val revision = controller.current.revision
    repeat(500) { controller.update(0.02f, 0.6f, 0.6f, 0.6f, 0f, 0f, 0f) }
    assertEquals(revision, controller.current.revision)
    controller.pause()
    tick(controller, 2.9f)
    assertEquals("Idle", controller.current.clip)
  }
}
