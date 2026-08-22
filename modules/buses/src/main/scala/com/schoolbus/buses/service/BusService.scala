package com.schoolbus.buses.service

import com.schoolbus.buses.model.{Bus, BusStatus}
import com.schoolbus.buses.repository.BusRepository
import com.schoolbus.common.errors.{AppError, FieldError}
import com.schoolbus.common.tenant.{Role, TenantContext}

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class BusService(busRepository: BusRepository)(implicit ec: ExecutionContext) {

  def create(ctx: TenantContext, plateNumber: String, capacity: Int): Future[Either[AppError, Bus]] =
    requireSchoolAdmin(ctx) match {
      case Left(err) => Future.successful(Left(err))
      case Right(schoolId) =>
        validateCapacity(capacity) match {
          case Left(err) => Future.successful(Left(err))
          case Right(_)  => createValidated(schoolId, plateNumber, capacity)
        }
    }

  def list(ctx: TenantContext, page: Int, size: Int): Future[Either[AppError, (Seq[Bus], Long)]] =
    requireSchoolAdmin(ctx) match {
      case Left(err)       => Future.successful(Left(err))
      case Right(schoolId) => busRepository.listBySchool(schoolId, page, size).map(Right(_))
    }

  // No separate cross-tenant check needed here (unlike
  // StudentService.getStudent's parent-link case): findById is already
  // scoped by ctx's schoolId, so a bus belonging to another school simply
  // doesn't come back - the tenant-scoped query naturally produces
  // NotFound instead of leaking that the bus exists elsewhere.
  def get(ctx: TenantContext, busId: UUID): Future[Either[AppError, Bus]] =
    requireSchoolAdmin(ctx) match {
      case Left(err) => Future.successful(Left(err))
      case Right(schoolId) =>
        busRepository.findById(schoolId, busId).map {
          case Some(bus) => Right(bus)
          case None      => Left(notFoundBus(busId))
        }
    }

  def updateStatus(ctx: TenantContext, busId: UUID, newStatus: BusStatus): Future[Either[AppError, Bus]] =
    requireSchoolAdmin(ctx) match {
      case Left(err) => Future.successful(Left(err))
      case Right(schoolId) =>
        busRepository.findById(schoolId, busId).flatMap {
          case None => Future.successful(Left(notFoundBus(busId)))
          case Some(existing) =>
            val updated = existing.copy(status = newStatus, updatedAt = Instant.now())
            busRepository.update(updated).map(Right(_))
        }
    }

  def updateCapacity(ctx: TenantContext, busId: UUID, newCapacity: Int): Future[Either[AppError, Bus]] =
    requireSchoolAdmin(ctx) match {
      case Left(err) => Future.successful(Left(err))
      case Right(schoolId) =>
        validateCapacity(newCapacity) match {
          case Left(err) => Future.successful(Left(err))
          case Right(_) =>
            busRepository.findById(schoolId, busId).flatMap {
              case None => Future.successful(Left(notFoundBus(busId)))
              case Some(existing) =>
                val updated = existing.copy(capacity = newCapacity, updatedAt = Instant.now())
                busRepository.update(updated).map(Right(_))
            }
        }
    }

  // ---- shared checks ----

  /** Pure comparison against the caller's own token claims - no DB access
    * needed. Every method in this service uses this same check (there's no
    * parent/driver-style read path for buses, unlike
    * StudentService.authorizeView), so "who's allowed to touch this
    * school's buses" can't drift between Create/List/Get/UpdateStatus/
    * UpdateCapacity. Checked before touching the repository in every
    * method - cheaper, and it avoids leaking bus existence to an
    * unauthorized caller via timing or error specifics.
    */
  private def requireSchoolAdmin(ctx: TenantContext): Either[AppError, UUID] =
    if (ctx.role != Role.SchoolAdmin)
      Left(AppError.Forbidden("Only a school admin may manage buses"))
    else
      ctx.schoolId match {
        case Some(schoolId) => Right(schoolId)
        case None           => Left(AppError.Forbidden("Only a school admin may manage buses"))
      }

  private def validateCapacity(capacity: Int): Either[AppError, Unit] =
    if (capacity > 0) Right(())
    else Left(AppError.ValidationFailed(List(FieldError("capacity", "must be positive"))))

  private def notFoundBus(id: UUID): AppError = AppError.NotFound("bus", id.toString)

  // Plate-number uniqueness is enforced per-school, not globally - unlike
  // auth's User.email, which is one global namespace shared by every
  // school (see UserRepository.findByEmail and the `unique` constraint on
  // users.email in the V1 migration). Two different schools each running
  // their own fleet can legitimately reuse the same plate number (e.g. if
  // they lease buses from the same regional pool, or just coincidentally
  // register the same plate through their own state DMV) - there is no
  // real-world reason those two rows should conflict with each other.
  // That's why existsByPlateNumber takes schoolId, and why the DB
  // constraint is UNIQUE(school_id, plate_number) rather than a bare
  // UNIQUE(plate_number).
  private def createValidated(schoolId: UUID, plateNumber: String, capacity: Int): Future[Either[AppError, Bus]] =
    busRepository.existsByPlateNumber(schoolId, plateNumber, excludingId = None).flatMap {
      case true =>
        Future.successful(
          Left(AppError.Conflict("DUPLICATE_PLATE_NUMBER", s"A bus with plate number $plateNumber already exists in this school"))
        )
      case false =>
        val now = Instant.now()
        val bus = Bus(
          id = UUID.randomUUID(),
          schoolId = schoolId,
          plateNumber = plateNumber,
          capacity = capacity,
          status = BusStatus.Active,
          createdAt = now,
          updatedAt = now
        )
        busRepository.create(bus).map(Right(_))
    }
}
