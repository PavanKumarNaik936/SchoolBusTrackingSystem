package com.schoolbus.auth.service

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class PasswordHasherSpec extends AnyWordSpec with Matchers {
  val hasher = new PasswordHasher

  "hash and verify" should {
    "accept the correct plaintext password" in {
      val hashed = hasher.hash("correct-horse-battery-staple")
      hasher.verify("correct-horse-battery-staple", hashed) shouldBe true
    }

    "reject an incorrect password" in {
      val hashed = hasher.hash("correct-horse-battery-staple")
      hasher.verify("wrong-password", hashed) shouldBe false
    }

    "produce a different hash each time (per-hash salt)" in {
      val first  = hasher.hash("same-password")
      val second = hasher.hash("same-password")
      first should not be second
    }
  }
}
