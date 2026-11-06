package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class LeashCollisionTest {
  @Test fun shortCollarGuideDoesNotDoubleBackThroughNearbyCurveSamples() {
    val socket = LeashPoint(0f, 0.42f, 0f)
    val hand = LeashPoint(0f, 0.8f, -1.6f)
    val body = LeashBody(listOf(LeashCapsule(
      LeashPoint(0f, 0.25f, -0.2f), LeashPoint(0f, 0.25f, 0.1f), 0.1f,
    )), LeashPoint(0f, 0.3f, 0f))
    val route = LeashCollision().route(LeashCurve().points(socket, hand, 0f), body, 64)
    assertClear(route, body.capsules)
    assertEquals(socket, route.first())
    assertEquals(hand, route.last())
    val turns = route.windowed(3).map { (a,b,c) ->
      val u = b-a; val v = c-b
      kotlin.math.acos(((u.x*v.x + u.y*v.y + u.z*v.z) / (u.length()*v.length())).coerceIn(-1f,1f))
    }
    assertTrue("Collar exit should bend gradually: $turns", turns.all { it < 0.65f })
  }

  @Test fun rightAngleIsRoundedWithoutMovingEitherAttachment() {
    val a = LeashPoint(0f,0f,0f)
    val b = LeashPoint(1f,1f,0f)
    val farBody = LeashBody(listOf(LeashCapsule(
      LeashPoint(5f,5f,5f), LeashPoint(6f,5f,5f), 0.1f,
    )), LeashPoint(-0.1f,0f,0f))
    val path = listOf(a, LeashPoint(0.02f,0f,0f), LeashPoint(1f,0f,0f), b)
    val route = LeashCollision().route(path,farBody,64)
    assertEquals(a,route.first())
    assertEquals(b,route.last())
    assertEquals(65,route.size)
    assertClear(route,farBody.capsules)
    val maxTurn = route.windowed(3).maxOf { (p,q,r) ->
      val u = q-p
      val v = r-q
      val cosine = (u.x*v.x + u.y*v.y + u.z*v.z) / (u.length()*v.length())
      kotlin.math.acos(cosine.coerceIn(-1f,1f))
    }
    assertTrue("A 90-degree corner should become several small turns",maxTurn < 0.5f)
  }

  @Test fun roundedDetourStillClearsBodyAtHigherRenderResolution() {
    val desired = LeashCurve().points(start,hand,0f)
    val route = LeashCollision().route(desired,body,64)
    assertEquals(start,route.first())
    assertEquals(hand,route.last())
    assertEquals(65,route.size)
    assertClear(route,body.capsules)
  }

  private val start = LeashPoint(-0.6f,0f,0f)
  private val hand = LeashPoint(0.7f,0f,0f)
  private val capsule = LeashCapsule(LeashPoint(-0.2f,0f,0f),LeashPoint(0.2f,0f,0f),0.15f)
  private val body = LeashBody(listOf(capsule),LeashPoint(-0.7f,0f,0f))

  private fun assertClear(points: List<LeashPoint>, capsules: List<LeashCapsule>) {
    assertTrue("Expected a visible route",points.size >= 2)
    for ((a,b) in points.zipWithNext()) {
      assertTrue("A rendered segment penetrates the body",capsules.none { it.intersects(a,b) })
    }
  }

  @Test fun crossingSegmentIsDetectedEvenWhenBothEndpointsAreOutside() {
    assertFalse(capsule.intersects(start,start))
    assertFalse(capsule.intersects(hand,hand))
    assertTrue(capsule.intersects(start,hand))
    assertEquals(0.04f,segmentDistanceSquared(LeashPoint(0f,0.2f,0f),LeashPoint(0f,0.2f,0f),
      LeashPoint(0f,0f,0f),LeashPoint(0f,0f,0f)),0.00001f)
  }

  @Test fun parallelSeparatedAndDegenerateSegmentsHaveFiniteDistances() {
    assertEquals(1f,segmentDistanceSquared(LeashPoint(0f,0f,0f),LeashPoint(1f,0f,0f),
      LeashPoint(0f,1f,0f),LeashPoint(1f,1f,0f)),0.00001f)
    assertEquals(0f,segmentDistanceSquared(start,hand,LeashPoint(0f,0f,0f),LeashPoint(0f,0f,0f)),0.00001f)
  }

  @Test fun detourPreservesAnchorsAndEveryRenderedSegmentClearsBody() {
    val desired = LeashCurve().points(start,hand,0f)
    val route = LeashCollision().route(desired,body)
    assertEquals(start,route.first())
    assertEquals(hand,route.last())
    assertEquals(25,route.size)
    assertClear(route,body.capsules)
    assertTrue((route[1]-start).x > 0f)
  }

  @Test fun animatedTranslationKeepsPreviousBendsClearWithoutChangingSides() {
    val solver = LeashCollision()
    val initial = solver.route(listOf(start,hand),body)
    assertClear(initial,body.capsules)
    val offset = LeashPoint(0.02f,0.04f,0.01f)
    val moved = LeashBody(listOf(LeashCapsule(capsule.a+offset,capsule.b+offset,capsule.radius)),body.neck+offset)
    val route = solver.route(listOf(start+offset,hand+offset),moved)
    assertClear(route,moved.capsules)
    assertEquals(start+offset,route.first())
    assertEquals(hand+offset,route.last())
    assertEquals(initial[initial.size/2].y+offset.y,route[route.size/2].y,0.0001f)
  }

  @Test fun impossibleHandInsideBodyReturnsNoPenetratingRope() {
    assertTrue(LeashCollision().route(listOf(start,LeashPoint(0f,0f,0f)),body).isEmpty())
  }

  @Test fun clearRopeRetainsSlackAndFixedHand() {
    val a = LeashPoint(-0.6f,1f,0f)
    val b = LeashPoint(0.7f,1f,0f)
    val desired = LeashCurve().points(a,b,0f)
    val route = LeashCollision().route(desired,body)
    assertClear(route,body.capsules)
    assertEquals(a,route.first())
    assertEquals(b,route.last())
    assertTrue(route.any { it.y < 0.95f })
  }

  @Test fun currentModelBindPoseSocketsAreOutsideProxies() {
    val neck = LeashPoint(0f,0.33646f,0.17795f)
    val head = LeashPoint(0f,0.39212f,0.21649f)
    val nose = LeashPoint(0f,0.38306f,0.32435f)
    val model = LeashBody(listOf(
      LeashCapsule(LeashPoint(0f,0.33751f,-0.14913f),LeashPoint(0f,0.32399f,0.09667f),0.094f),
      LeashCapsule(neck,head,0.054f),
      LeashCapsule(head+(nose-head)*0.25f,nose,0.074f),
    ),neck)
    for (socket in listOf(LeashPoint(0f,0.41089f,0.15952f),LeashPoint(-0.08838f,0.37338f,0.20936f))) {
      assertTrue(model.capsules.none { it.intersects(socket,socket) })
      val route = LeashCollision().route(LeashCurve().points(socket,LeashPoint(0f,0.6f,-1f),0f),model)
      assertClear(route,model.capsules)
      assertEquals(socket,route.first())
    }
  }
}
