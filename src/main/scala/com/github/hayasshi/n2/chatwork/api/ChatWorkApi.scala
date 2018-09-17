package com.github.hayasshi.n2.chatwork.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Sink
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import spray.json.DefaultJsonProtocol._
import spray.json.JsonFormat

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.reflectiveCalls

object ChatWorkApi {

  val baseUrl = "https://api.chatwork.com/v2"

  case class ErrorResponse(errors: Seq[String])

  case class ChatWorkApiResponseError(message: String) extends Exception(message)

  val jsonFormatErrorResponse: JsonFormat[ErrorResponse] = jsonFormat1(ErrorResponse)

}

trait EndPointModule {
  type Req
  type Res
  def jsonFormatResponse: JsonFormat[Res]
  def buildRequest(request: Req): HttpRequest
}

trait ChatWorkApiClient { self: EndPointModule =>

  def request(req: Req)(implicit mat: ActorMaterializer): Future[Res] = {
    implicit val system: ActorSystem = mat.system
    implicit val ec: ExecutionContext = mat.executionContext

    for {
      httpResponse <- Http().singleRequest(buildRequest(req))
      response <- extractResponse(httpResponse)
    } yield response
  }

  def extractResponse(httpResponse: HttpResponse)(implicit mat: Materializer): Future[Res] = {
    implicit val ec: ExecutionContext = mat.executionContext
    httpResponse.entity.dataBytes.runReduce(_ ++ _).map { bs =>
      if (httpResponse.status.isSuccess())
        ???
      else
        ???
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
  login_mail: String)

trait GetMe extends EndPointModule {
  type Req = GetMeRequest
  type Res = GetMeResponse

  val jsonFormatResponse: JsonFormat[Res] = jsonFormat6(GetMeResponse)

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
  avatar_image_url: String)
trait GetMemberList {
  type Req = GetMemberListRequest
  type Res = GetMemberListResponse

  implicit val jsonFormatResponse: JsonFormat[Res] = jsonFormat6(GetMemberListResponse)

  def buildRequest(req: Req): HttpRequest = {
    HttpRequest(GET, Uri(s"${ChatWorkApi.baseUrl}/rooms/${req.room_id}/members"))
  }
}

case class CreateTaskRequest(
  room_id: Long,
  body: String,
  limit: Long, // epoch times
  to_ids: Seq[Long])
case class CreateTaskResponse(task_ids: Seq[Long])
trait CreateTask {
  type Req = CreateTaskRequest
  type Res = CreateTaskResponse

  val jsonFormatRequest: JsonFormat[Req] = jsonFormat4(CreateTaskRequest)
  val jsonFormatResponse: JsonFormat[Res] = jsonFormat1(CreateTaskResponse)

  def buildRequest(req: Req): HttpRequest = {
    val body = jsonFormatRequest.write(req).compactPrint
    val entity = HttpEntity(ContentTypes.`application/json`, body)
    HttpRequest(POST, Uri(s"${ChatWorkApi.baseUrl}/rooms/${req.room_id}/tasks"), entity = entity)
  }
}
