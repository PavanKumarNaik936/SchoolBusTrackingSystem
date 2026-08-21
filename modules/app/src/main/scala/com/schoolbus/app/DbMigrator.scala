package com.schoolbus.app

import org.flywaydb.core.Flyway

/** Wraps Flyway so nothing else needs to know migration file locations or
  * how to build its config. Takes a raw JDBC url/user/password rather than
  * reusing `common.db.DatabaseConfig.db` - that's a Slick `Database` (a
  * connection pool wrapper), and Flyway needs its own JDBC `DataSource`, so
  * there's no handle to share between the two even if we wanted to.
  *
  * Called from two places with two different targets: `Main` (the real
  * configured Postgres) and `AppIntegrationSpec` (a Testcontainers Postgres).
  * Both go through this one function so the integration test is actually
  * exercising the same migration path production uses, not a parallel one.
  */
object DbMigrator {
  def migrate(jdbcUrl: String, user: String, password: String): Unit = {
    Flyway
      .configure()
      .dataSource(jdbcUrl, user, password)
      .load()
      .migrate()
    ()
  }
}
