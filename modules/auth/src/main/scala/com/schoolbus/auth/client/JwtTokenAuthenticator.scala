package com.schoolbus.auth.client

import com.schoolbus.auth.service.JwtService
import com.schoolbus.common.auth.TokenAuthenticator
import com.schoolbus.common.tenant.TenantContext

/** The real TokenAuthenticator, backed by JwtService. Lives in `auth`
  * since it's the only module that knows how tokens are signed/decoded.
  */
class JwtTokenAuthenticator(jwtService: JwtService) extends TokenAuthenticator {
  def authenticate(bearerToken: String): Either[String, TenantContext] =
    jwtService.decode(bearerToken).left.map(_ => "Invalid or expired access token").map { claims =>
      TenantContext(claims.userId, claims.role, claims.schoolId)
    }
}