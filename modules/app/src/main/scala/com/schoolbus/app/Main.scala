package com.schoolbus.app

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.schoolbus.common.db.DatabaseConfig
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/** The composition root's entry point: read config, migrate the schema,
  * wire every module together (`AppWiring`), and bind an HTTP server. Kept
  * intentionally thin - anything beyond "read config once, call the object
  * that does the real work" belongs in `AppWiring`/`AppRoutes`/`DbMigrator`,
  * not here.
  */
object Main {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()

    implicit val system: ActorSystem = ActorSystem("schoolbus")
    implicit val ec = system.dispatcher

    // Flyway needs a raw JDBC url/user/password, not the Slick `Database`
    // handle `DatabaseConfig.db` wraps - see DbMigrator's doc comment. Read
    // from the same "schoolbus.db" keys DatabaseConfig itself reads, so
    // there's exactly one source of truth for connection settings even
    // though two different libraries end up consuming them.
    val dbConfig = config.getConfig("schoolbus.db")

    // Fail fast: if the schema can't be migrated, don't bind a listener that
    // would just serve errors against a stale/missing schema.
    DbMigrator.migrate(
      jdbcUrl = dbConfig.getString("url"),
      user = dbConfig.getString("user"),
      password = dbConfig.getString("password")
    )

    val db        = DatabaseConfig.db
    val jwtSecret = config.getString("schoolbus.auth.jwt-secret")

    val components = AppWiring.build(db, jwtSecret)
    val routes     = AppRoutes(db, components.routes)

    val host = config.getString("schoolbus.http.host")
    val port = config.getInt("schoolbus.http.port")

    val bindingFuture = Http().newServerAt(host, port).bind(routes)

    bindingFuture.onComplete {
      case Success(binding) =>
        system.log.info(s"Listening on ${binding.localAddress}")
      case Failure(ex) =>
        system.log.error(ex, s"Failed to bind to $host:$port")
        system.terminate()
    }

    // Unbind before tearing down the actor system, so in-flight requests get
    // a chance to finish rather than being cut off mid-response.
    sys.addShutdownHook {
      val shutdown = bindingFuture
        .flatMap(_.terminate(hardDeadline = 10.seconds))
        .flatMap(_ => system.terminate())
      Await.result(shutdown, 15.seconds)
      ()
    }
  }
}
