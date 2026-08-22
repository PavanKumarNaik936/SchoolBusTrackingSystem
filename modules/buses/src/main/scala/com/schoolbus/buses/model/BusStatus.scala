package com.schoolbus.buses.model

sealed trait BusStatus {
  /** The one canonical string form of this status - used in the DB column
    * and anywhere else a status crosses a boundary (wire format, DB
    * column). Everything reads through here instead of relying on Scala's
    * default toString, same reasoning as Role.wireName in `common`.
    */
  def wireName: String
}
object BusStatus {
  case object Active extends BusStatus      { val wireName = "ACTIVE"      }
  case object Inactive extends BusStatus    { val wireName = "INACTIVE"    }
  case object Maintenance extends BusStatus { val wireName = "MAINTENANCE" }

  /** Parses a status string coming out of the DB or an incoming request.
    * Returns Either rather than throwing, since an unrecognized value from
    * a request body is an expected failure mode the routes layer needs to
    * handle gracefully, not a bug.
    */
  def fromString(s: String): Either[String, BusStatus] = s match {
    case "ACTIVE"      => Right(Active)
    case "INACTIVE"    => Right(Inactive)
    case "MAINTENANCE" => Right(Maintenance)
    case other         => Left(s"Unknown bus status: $other")
  }
}
