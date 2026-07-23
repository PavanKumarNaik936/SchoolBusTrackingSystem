package com.schoolbus.auth.service

import org.mindrot.jbcrypt.BCrypt

/** Wraps bcrypt so the rest of the codebase never imports jbcrypt directly.
  * If we ever migrate to a stronger algorithm (argon2, say), this is the
  * only file that changes.
  *
  * A common beginner mistake this avoids: rolling your own hashing with
  * plain SHA-256. SHA-256 is fast - which is exactly wrong for passwords,
  * since it makes brute-forcing cheap. Bcrypt is deliberately slow and
  * includes a per-hash salt automatically, both of which matter here.
  */
class PasswordHasher {
  private val cost = 12 // work factor; higher = slower to hash AND slower to brute-force

  def hash(plaintext: String): String =
    BCrypt.hashpw(plaintext, BCrypt.gensalt(cost))

  def verify(plaintext: String, hashed: String): Boolean =
    BCrypt.checkpw(plaintext, hashed)
}
