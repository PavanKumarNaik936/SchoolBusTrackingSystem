







ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "com.schoolbus"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(common, auth, schools, students)
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
