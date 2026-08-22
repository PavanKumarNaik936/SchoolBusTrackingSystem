package com.schoolbus.buses.service

import com.schoolbus.buses.model.{Bus, BusStatus}
import com.schoolbus.buses.repository.BusRepository
import com.schoolbus.common.errors.AppError
import com.schoolbus.common.tenant.{Role, TenantContext}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/** In-memory fake standing in for the Slick-backed repository - same
  * pattern as auth's FakeUserRepository: BusService's actual logic
  * (authorization, capacity validation, per-school plate uniqueness) gets
  * tested with zero database involved.
  */
class FakeBusRepository extends BusRepository {
  private val byId = mutable.Map.empty[UUID, Bus]

  def seed(bus: Bus): Unit = byId.update(bus.id, bus)

  def create(bus: Bus): Future[Bus] = {
    byId.update(bus.id, bus)
    Future.successful(bus)
  }

  def findById(schoolId: UUID, id: UUID): Future[Option[Bus]] =
    Future.successful(byId.get(id).filter(_.schoolId == schoolId))

  def listBySchool(schoolId: UUID, page: Int, size: Int): Future[(Seq[Bus], Long)] = {
    val matching = byId.values.filter(_.schoolId == schoolId).toSeq.sortBy(_.plateNumber)
    val paged = matching.drop((page - 1) * size).take(size)
    Future.successful((paged, matching.size.toLong))
  }

  def update(bus: Bus): Future[Bus] = {
    byId.update(bus.id, bus)
    Future.successful(bus)
  }

  def existsByPlateNumber(schoolId: UUID, plateNumber: String, excludingId: Option[UUID]): Future[Boolean] =
    Future.successful(
      byId.values.exists(b => b.schoolId == schoolId && b.plateNumber == plateNumber && !excludingId.contains(b.id))
    )
}

class BusServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private def schoolAdminCtx(schoolId: UUID): TenantContext =
    TenantContext(userId = UUID.randomUUID(), role = Role.SchoolAdmin, schoolId = Some(schoolId))

  private def driverCtx(schoolId: UUID): TenantContext =
    TenantContext(userId = UUID.randomUUID(), role = Role.Driver, schoolId = Some(schoolId))

  private def newBus(schoolId: UUID, plateNumber: String): Bus = {
    val now = Instant.now()
    Bus(
      id = UUID.randomUUID(),
      schoolId = schoolId,
      plateNumber = plateNumber,
      capacity = 40,
      status = BusStatus.Active,
      createdAt = now,
      updatedAt = now
    )
  }

  private case class Fixture(service: BusService, repo: FakeBusRepository, schoolId: UUID)

  private def newFixture(): Fixture = {
    val repo = new FakeBusRepository
    Fixture(new BusService(repo), repo, UUID.randomUUID())
  }

  "create" should {
    "reject a non-SchoolAdmin caller" in {
      val f = newFixture()
      f.service.create(driverCtx(f.schoolId), "PLATE-1", 40).futureValue shouldBe a[Left[_, _]]
    }

    "reject a non-positive capacity" in {
      val f = newFixture()
      f.service.create(schoolAdminCtx(f.schoolId), "PLATE-1", 0).futureValue shouldBe
        Left(AppError.ValidationFailed(List(com.schoolbus.common.errors.FieldError("capacity", "must be positive"))))
    }

    "reject a duplicate plate number within the same school" in {
      val f = newFixture()
      f.repo.seed(newBus(f.schoolId, "PLATE-1"))

      val result = f.service.create(schoolAdminCtx(f.schoolId), "PLATE-1", 40).futureValue
      result.isLeft shouldBe true
      result.left.toOption.map(_.code) shouldBe Some("DUPLICATE_PLATE_NUMBER")
    }

    "allow the same plate number in a different school - uniqueness is per-school, not global" in {
      val f = newFixture()
      val otherSchoolId = UUID.randomUUID()
      f.repo.seed(newBus(otherSchoolId, "PLATE-1"))

      f.service.create(schoolAdminCtx(f.schoolId), "PLATE-1", 40).futureValue shouldBe a[Right[_, _]]
    }
  }

  "get" should {
    "return NotFound for a bus belonging to a different school than the caller's TenantContext" in {
      val f = newFixture()
      val otherSchoolId = UUID.randomUUID()
      val bus = newBus(otherSchoolId, "PLATE-1")
      f.repo.seed(bus)

      f.service.get(schoolAdminCtx(f.schoolId), bus.id).futureValue shouldBe Left(AppError.NotFound("bus", bus.id.toString))
    }
  }

  "updateStatus" should {
    "reject a non-SchoolAdmin caller" in {
      val f = newFixture()
      val bus = newBus(f.schoolId, "PLATE-1")
      f.repo.seed(bus)

      f.service.updateStatus(driverCtx(f.schoolId), bus.id, BusStatus.Maintenance).futureValue shouldBe a[Left[_, _]]
    }

    "return NotFound for a bus belonging to a different school than the caller's TenantContext" in {
      val f = newFixture()
      val otherSchoolId = UUID.randomUUID()
      val bus = newBus(otherSchoolId, "PLATE-1")
      f.repo.seed(bus)

      f.service.updateStatus(schoolAdminCtx(f.schoolId), bus.id, BusStatus.Maintenance).futureValue shouldBe
        Left(AppError.NotFound("bus", bus.id.toString))
    }
  }

  "updateCapacity" should {
    "reject a non-SchoolAdmin caller" in {
      val f = newFixture()
      val bus = newBus(f.schoolId, "PLATE-1")
      f.repo.seed(bus)

      f.service.updateCapacity(driverCtx(f.schoolId), bus.id, 50).futureValue shouldBe a[Left[_, _]]
    }
  }
}
