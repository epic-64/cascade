package shared.session

/** Trait for entities that track connection state. Used by player models in games to support reconnection.
  */
trait PlayerConnection:
  def connected: Boolean
  def disconnectedAt: Option[Long]

/** Operations for checking player connection state and grace periods */
object PlayerConnectionOps:

  /** Default grace period for reconnection (60 seconds) */
  val DefaultGracePeriodMs: Long = 60000

  /** Check if a player can rejoin within the grace period.
    *
    * @param player
    *   The player connection state
    * @param gracePeriodMs
    *   How long after disconnect a player can rejoin
    * @return
    *   true if player can rejoin
    */
  def canRejoin(player: PlayerConnection, gracePeriodMs: Long = DefaultGracePeriodMs): Boolean =
    player.disconnectedAt match
      case Some(disconnectTime) =>
        val elapsed = System.currentTimeMillis() - disconnectTime
        elapsed < gracePeriodMs
      case None =>
        // Player is still connected or never disconnected
        true

  /** Check if a player's disconnect time has exceeded the grace period. Useful for cleanup operations.
    *
    * @param player
    *   The player connection state
    * @param gracePeriodMs
    *   How long after disconnect before considered expired
    * @return
    *   true if grace period has expired
    */
  def isGracePeriodExpired(player: PlayerConnection, gracePeriodMs: Long = DefaultGracePeriodMs): Boolean =
    player.disconnectedAt match
      case Some(disconnectTime) =>
        val elapsed = System.currentTimeMillis() - disconnectTime
        elapsed >= gracePeriodMs
      case None => false

  /** Helper to mark a player as disconnected. Returns the values to set on the player model.
    */
  def disconnectValues: (Boolean, Option[Long]) =
    (false, Some(System.currentTimeMillis()))

  /** Helper to mark a player as reconnected. Returns the values to set on the player model.
    */
  def reconnectValues: (Boolean, Option[Long]) =
    (true, None)
