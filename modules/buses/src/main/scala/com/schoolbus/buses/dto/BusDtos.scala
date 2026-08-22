package com.schoolbus.buses.dto

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import com.schoolbus.buses.model.Bus

final case class CreateBusRequest(plateNumber: String, capacity: Int)
object CreateBusRequest {
  implicit val decoder: Decoder[CreateBusRequest] = deriveDecoder
}

final case class UpdateBusStatusRequest(status: String)
object UpdateBusStatusRequest {
  implicit val decoder: Decoder[UpdateBusStatusRequest] = deriveDecoder
}

final case class UpdateBusCapacityRequest(capacity: Int)
object UpdateBusCapacityRequest {
  implicit val decoder: Decoder[UpdateBusCapacityRequest] = deriveDecoder
}

final case class BusResponse(id: String, plateNumber: String, capacity: Int, status: String, createdAt: String)
object BusResponse {
  implicit val encoder: Encoder[BusResponse] = deriveEncoder

  def from(bus: Bus): BusResponse =
    BusResponse(bus.id.toString, bus.plateNumber, bus.capacity, bus.status.wireName, bus.createdAt.toString)
}

final case class BusPageResponse(data: Seq[BusResponse], page: Int, size: Int, totalElements: Long)
object BusPageResponse {
  implicit val encoder: Encoder[BusPageResponse] = deriveEncoder

  def from(buses: Seq[Bus], page: Int, size: Int, totalElements: Long): BusPageResponse =
    BusPageResponse(buses.map(BusResponse.from), page, size, totalElements)
}
