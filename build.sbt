
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "com.schoolbus"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(common, auth, schools, students, buses, app)
  .settings(publish / skip := true)

// Feature modules get added here as we build them:
// .dependsOn(common) for each one, per the dependency-flow diagram from Phase 2.
lazy val common = (project in file("modules/common"))
  .settings(
    name := "common",
    libraryDependencies ++= Dependencies.common
  )

lazy val auth = (project in file("modules/auth"))
  .dependsOn(common)
  .settings(
    name := "auth",
    libraryDependencies ++= Dependencies.auth
  )

// Prerequisite for the students module: School and Route just need to
// exist so Students can validate schoolId/routeId against them. No routes
// (HTTP) layer yet - nothing in the current spec calls for Schools/Routes
// CRUD endpoints.
lazy val schools = (project in file("modules/schools"))
  .dependsOn(common)
  .settings(
    name := "schools",
    libraryDependencies ++= Dependencies.schools
  )

lazy val students = (project in file("modules/students"))
  .dependsOn(common, schools)
  .settings(
    name := "students",
    libraryDependencies ++= Dependencies.students
  )

// Buses only needs schoolId to exist as a bare UUID it can scope queries
// by, same as auth's users.school_id - it doesn't need to load a School
// row, so it depends on `common` only, not `schools` (see Tables.scala's
// note on why BusesTable has no Scala-level foreignKey() to schools).
lazy val buses = (project in file("modules/buses"))
  .dependsOn(common)
  .settings(
    name := "buses",
    libraryDependencies ++= Dependencies.buses
  )

// The composition root: the only module that constructs real (Slick-backed)
// wiring for every other module, runs migrations, and binds an HTTP server.
// Depends on all five feature modules so it can wire them together, but none
// of them depend back on it - same one-way dependency-flow rule as everywhere
// else, just terminating here instead of at `common`.
lazy val app = (project in file("modules/app"))
  .dependsOn(common, auth, schools, students, buses)
  .settings(
    name := "app",
    libraryDependencies ++= Dependencies.app,
    Compile / mainClass := Some("com.schoolbus.app.Main")
  )
