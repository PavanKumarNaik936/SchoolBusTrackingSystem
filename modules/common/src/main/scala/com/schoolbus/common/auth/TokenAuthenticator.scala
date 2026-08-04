package com.schoolbus.common.auth

import com.schoolbus.common.tenant.TenantContext

/** Lives in `common` for the same reason UserClient does: a module's
  * routes layer (e.g. students) needs to turn a bearer token into a
  * TenantContext, but shouldn't have to depend on all of `auth` (JWT
  * signing internals, password hashing, etc.) just to do that. `auth`
  * provides the real implementation; the application's composition root
  * wires it into each module's routes.
  */
trait TokenAuthenticator {
  def authenticate(bearerToken: String): Either[String, TenantContext]
}