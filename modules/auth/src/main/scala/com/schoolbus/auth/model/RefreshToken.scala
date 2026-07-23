package com.schoolbus.auth.model

import java.time.Instant
import java.util.UUID

/** A server-side record of an issued refresh token. We never store the raw
  * token - only a SHA-256 hash of it - for the same reason we never store
  * raw passwords: if the DB is ever read by someone unauthorized, they get
  * hashes, not usable credentials.
  *
  * revokedAt being set is what makes "logout" mean something. A bare JWT
  * refresh token can't be un-issued before it expires; this record can be
  * marked revoked at any time.
  */
final case class RefreshToken(
    id: UUID,
    userId: UUID,
    tokenHash: String,
    expiresAt: Instant,
    revokedAt: Option[Instant],
    createdAt: Instant
) {
  def isValid(now: Instant): Boolean =
    revokedAt.isEmpty && now.isBefore(expiresAt)
}
