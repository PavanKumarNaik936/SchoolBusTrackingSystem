package com.schoolbus.auth.dto

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import com.schoolbus.auth.service.TokenPair

final case class LoginRequest(email: String, password: String)
object LoginRequest {
  implicit val decoder: Decoder[LoginRequest] = deriveDecoder
}

final case class RefreshRequest(refreshToken: String)
object RefreshRequest {
  implicit val decoder: Decoder[RefreshRequest] = deriveDecoder
}

final case class TokenResponse(accessToken: String, refreshToken: String, expiresIn: Long, role: String)
object TokenResponse {
  implicit val encoder: Encoder[TokenResponse] = deriveEncoder

  def from(pair: TokenPair): TokenResponse =
    TokenResponse(pair.accessToken, pair.refreshToken, pair.expiresIn, pair.role)
}

final case class PasswordResetRequest(email: String)
object PasswordResetRequest {
  implicit val decoder: Decoder[PasswordResetRequest] = deriveDecoder
}

final case class PasswordResetConfirmRequest(token: String, newPassword: String)
object PasswordResetConfirmRequest {
  implicit val decoder: Decoder[PasswordResetConfirmRequest] = deriveDecoder
}

final case class MessageResponse(message: String)
object MessageResponse {
  implicit val encoder: Encoder[MessageResponse] = deriveEncoder
}
