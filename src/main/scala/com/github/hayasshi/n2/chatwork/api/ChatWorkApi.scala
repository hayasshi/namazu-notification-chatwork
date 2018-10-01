package com.github.hayasshi.n2.chatwork.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.{ ActorMaterializer, Materializer }
import com.github.hayasshi.n2.chatwork.api.ChatWorkApi.ChatWorkApiResponseError
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.reflectiveCalls

object ChatWorkApi {

  val baseUrl = "https://api.chatwork.com/v2"

  case class ErrorResponse(errors: Seq[String])

  case class ChatWorkApiResponseError(message: String) extends Exception(message)

  val errorResponseDecoder: Decoder[ErrorResponse] = deriveDecoder

}

case class ChatWorkApiSetting(
    apiToken: String
)

trait EndPointModule {
  type Req
  type Res
  def apiResponseDecoder: Decoder[Res]
  def buildRequest(request: Req): HttpRequest
}

trait ChatWorkApiClient { self: EndPointModule =>

  def request(setting: ChatWorkApiSetting)(req: Req)(implicit mat: ActorMaterializer): Future[Res] = {
    implicit val system: ActorSystem = mat.system
    implicit val ec: ExecutionContext = mat.executionContext

    val buildReq = buildRequest(req)
    val httpRequest = buildReq.withHeaders(RawHeader("X-ChatWorkToken", setting.apiToken) +: buildReq.headers)
    for {
      httpResponse <- Http().singleRequest(httpRequest)
      response <- extractResponse(httpResponse)
    } yield response
  }

  def extractResponse(httpResponse: HttpResponse)(implicit mat: Materializer): Future[Res] = {
    implicit val ec: ExecutionContext = mat.executionContext
    httpResponse.entity.dataBytes.runReduce(_ ++ _).map { bs =>
      println(bs.utf8String)
      if (httpResponse.status.isSuccess())
        decode[Res](bs.utf8String)(apiResponseDecoder) match {
          case Right(r) => r
          case Left(e)  => throw e
        }
      else {
        decode(bs.utf8String)(ChatWorkApi.errorResponseDecoder) match {
          case Right(r) => throw ChatWorkApiResponseError("chatwork api occurred error.\n" + r.errors.mkString("\n"))
          case Left(e)  => throw e
        }
      }
    }
  }

}

case class GetMeRequest()
case class GetMeResponse(
    account_id: Long,
    room_id: Long,
    name: String,
    organization_id: Long,
    avatar_image_url: String,
    login_mail: String
)

trait GetMe extends EndPointModule {
  type Req = GetMeRequest
  type Res = GetMeResponse

  val apiResponseDecoder: Decoder[Res] = deriveDecoder

  def buildRequest(request: Req): HttpRequest = {
    HttpRequest(GET, Uri(s"${ChatWorkApi.baseUrl}/me"))
  }
}

case class GetMemberListRequest(room_id: Long)
case class GetMemberListResponse(
    account_id: Long,
    role: String,
    name: String,
    organization_id: Long,
    organization_name: String,
    avatar_image_url: String
)
trait GetMemberList extends EndPointModule {
  type Req = GetMemberListRequest
  type Res = Vector[GetMemberListResponse]

  val apiResponseDecoder: Decoder[Res] = Decoder.decodeVector(deriveDecoder[GetMemberListResponse])

  def buildRequest(req: Req): HttpRequest = {
    HttpRequest(GET, Uri(s"${ChatWorkApi.baseUrl}/rooms/${req.room_id}/members"))
  }
}

case class CreateTaskRequest(
    room_id: Long,
    body: String,
    limit: Option[Long], // epoch times
    to_ids: Seq[Long]
)
case class CreateTaskResponse(task_ids: Seq[Long])
trait CreateTask extends EndPointModule {
  type Req = CreateTaskRequest
  type Res = CreateTaskResponse

  val apiRequestEncoder: Encoder[Req] = deriveEncoder
  val apiResponseDecoder: Decoder[Res] = deriveDecoder

  def buildRequest(req: Req): HttpRequest = {
    val data = collection.mutable.Map(
      "body" -> req.body,
      "to_ids" -> req.to_ids.map(_.toString).mkString(",")
    )
    req.limit.foreach(l => data.put("limit", l.toString))
    val formData = FormData(
      data.toMap
    )
    HttpRequest(POST, Uri(s"${ChatWorkApi.baseUrl}/rooms/${req.room_id}/tasks"), entity = formData.toEntity)
  }
}
