package shared.session

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PlayerConnectionOpsSpec extends AnyFunSuite with Matchers:

  // Test helper - a simple player implementation for testing
  case class TestPlayer(connected: Boolean, disconnectedAt: Option[Long]) extends PlayerConnection

  test("canRejoin returns true for connected player with no disconnect time"):
    val player = TestPlayer(connected = true, disconnectedAt = None)
    
    PlayerConnectionOps.canRejoin(player) shouldBe true

  test("canRejoin returns true for disconnected player within grace period"):
    val fiveSecondsAgo = System.currentTimeMillis() - 5000
    val player = TestPlayer(connected = false, disconnectedAt = Some(fiveSecondsAgo))
    
    PlayerConnectionOps.canRejoin(player, gracePeriodMs = 60000) shouldBe true

  test("canRejoin returns false for disconnected player past grace period"):
    val twoMinutesAgo = System.currentTimeMillis() - 120000
    val player = TestPlayer(connected = false, disconnectedAt = Some(twoMinutesAgo))
    
    PlayerConnectionOps.canRejoin(player, gracePeriodMs = 60000) shouldBe false

  test("canRejoin respects custom grace period"):
    val tenSecondsAgo = System.currentTimeMillis() - 10000
    val player = TestPlayer(connected = false, disconnectedAt = Some(tenSecondsAgo))
    
    // Within 15 second grace period
    PlayerConnectionOps.canRejoin(player, gracePeriodMs = 15000) shouldBe true
    // Outside 5 second grace period
    PlayerConnectionOps.canRejoin(player, gracePeriodMs = 5000) shouldBe false

  test("isGracePeriodExpired returns false for connected player"):
    val player = TestPlayer(connected = true, disconnectedAt = None)
    
    PlayerConnectionOps.isGracePeriodExpired(player) shouldBe false

  test("isGracePeriodExpired returns false for recently disconnected player"):
    val fiveSecondsAgo = System.currentTimeMillis() - 5000
    val player = TestPlayer(connected = false, disconnectedAt = Some(fiveSecondsAgo))
    
    PlayerConnectionOps.isGracePeriodExpired(player, gracePeriodMs = 60000) shouldBe false

  test("isGracePeriodExpired returns true for player disconnected past grace period"):
    val twoMinutesAgo = System.currentTimeMillis() - 120000
    val player = TestPlayer(connected = false, disconnectedAt = Some(twoMinutesAgo))
    
    PlayerConnectionOps.isGracePeriodExpired(player, gracePeriodMs = 60000) shouldBe true

  test("disconnectValues returns correct tuple"):
    val (connected, disconnectedAt) = PlayerConnectionOps.disconnectValues
    
    connected shouldBe false
    disconnectedAt shouldBe defined

  test("reconnectValues returns correct tuple"):
    val (connected, disconnectedAt) = PlayerConnectionOps.reconnectValues
    
    connected shouldBe true
    disconnectedAt shouldBe None

  test("default grace period is 60 seconds"):
    PlayerConnectionOps.DefaultGracePeriodMs shouldBe 60000L

