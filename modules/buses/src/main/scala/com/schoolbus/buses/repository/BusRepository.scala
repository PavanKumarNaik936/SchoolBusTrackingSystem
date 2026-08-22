package com.schoolbus.buses.repository

import com.schoolbus.buses.model.{Bus, Tables}
import slick.jdbc.PostgresProfile.api._
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/** Defined as a trait, not a concrete class, so BusService can depend on
  * this interface and be tested with a fake in-memory implementation - no
  * real Postgres needed to run the service-layer unit tests. Same rule as
  * auth.repository.UserRepository/students.repository.StudentRepository.
  *
  * Every method that reads or writes a specific bus takes schoolId
  * explicitly - this is the tenant-isolation discipline from
  * TenantContext's doc comment, applied here so a developer can't forget
  * to scope a bus query by school.
  */
trait BusRepository {
  def create(bus: Bus): Future[Bus]
  def findById(schoolId: UUID, id: UUID): Future[Option[Bus]]
  def listBySchool(schoolId: UUID, page: Int, size: Int): Future[(Seq[Bus], Long)]
  def update(bus: Bus): Future[Bus]
  def existsByPlateNumber(schoolId: UUID, plateNumber: String, excludingId: Option[UUID]): Future[Boolean]
}

class SlickBusRepository(db: Database)(implicit ec: ExecutionContext) extends BusRepository {
  import Tables._

  def create(bus: Bus): Future[Bus] =
    db.run(buses += bus).map(_ => bus)

  def findById(schoolId: UUID, id: UUID): Future[Option[Bus]] =
    db.run(buses.filter(b => b.schoolId === schoolId && b.id === id).result.headOption)

  // Assumes the caller (service layer) already validated page >= 1 and
  // size > 0 - this method trusts its inputs rather than re-validating,
  // same "routes validate shape, service validates business rules" split
  // used by SlickStudentRepository.listBySchool.
  def listBySchool(schoolId: UUID, page: Int, size: Int): Future[(Seq[Bus], Long)] = {
    val scoped = buses.filter(_.schoolId === schoolId)
    for {
      items <- db.run(scoped.sortBy(_.plateNumber).drop((page - 1) * size).take(size).result)
      total <- db.run(scoped.length.result)
    } yield (items, total.toLong)
  }

  def update(bus: Bus): Future[Bus] =
    db.run(buses.filter(b => b.schoolId === bus.schoolId && b.id === bus.id).update(bus)).map(_ => bus)

  def existsByPlateNumber(schoolId: UUID, plateNumber: String, excludingId: Option[UUID]): Future[Boolean] = {
    val base = buses.filter(b => b.schoolId === schoolId && b.plateNumber === plateNumber)
    val scoped = excludingId.fold(base)(id => base.filter(_.id =!= id))
    db.run(scoped.exists.result)
  }
}
