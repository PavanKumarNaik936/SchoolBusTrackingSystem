package com.schoolbus.auth.model

import java.time.Instant
import java.util.UUID

/** Same storage principle as RefreshToken: only a SHA-256 hash of the raw
  * token is ever persisted, never the token itself. usedAt is what makes
  * the token single-use - a bare opaque token has no way to expire itself
  * the instant it's consumed, so we track that server-side instead.
  */
final case class PasswordResetToken(
    id: UUID,
    userId: UUID,
    tokenHash: String,
    expiresAt: Instant,
    usedAt: Option[Instant],
    createdAt: Instant
) {
  def isValid(now: Instant): Boolean =
    usedAt.isEmpty && now.isBefore(expiresAt)
}