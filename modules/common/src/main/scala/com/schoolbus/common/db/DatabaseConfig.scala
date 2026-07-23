package com.schoolbus.common.db

import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.PostgresProfile.api._

/** Every module's repository layer gets its Database handle from here,
  * rather than each module independently building its own HikariCP pool
  * from scratch. One connection pool, one config path, one place to tune
  * pool size when we get to production readiness (Phase 10).
  *
  * Config is read from the "schoolbus.db" path - see reference.conf for
  * the shape it expects.
  */
object DatabaseConfig {
  private val config: Config = ConfigFactory.load()

  lazy val db: Database = Database.forConfig("schoolbus.db", config)
}
