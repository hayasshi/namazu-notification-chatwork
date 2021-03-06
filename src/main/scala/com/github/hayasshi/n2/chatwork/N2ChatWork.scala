package com.github.hayasshi.n2.chatwork

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.github.hayasshi.n2.chatwork.api._

import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutor }
import scala.util.{ Failure, Success }

object N2ChatWork extends App {

  implicit val system: ActorSystem = ActorSystem("n2chatwork")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val config = system.settings.config

  val roomId = system.settings.config.getInt("n2.chatwork.target-room-id")
  val thresholdIntensity = system.settings.config.getInt("n2.chatwork.threshold-intensity")

  val apiSetting = ChatWorkApiSetting(config.getString("n2.chatwork.api.token"))
  val getMeRequest = (new ChatWorkApiClient with GetMe).request(apiSetting) _
  val getMemberListRequest = (new ChatWorkApiClient with GetMemberList).request(apiSetting) _
  val createTaskRequest = (new ChatWorkApiClient with CreateTask).request(apiSetting) _

  import io.circe.generic.auto._
  import io.circe.parser._
  val route = path("earthquake") {
    post {
      extractRequestContext { ctx =>

        def actionWhenEarthQuake(quake: YahooEarthQuake): Unit = {
          val intensity = quake.intensity.toInt
          val maxIntensity = quake.intensity.toInt
          ctx.log.debug(s"RoomId($roomId), token=${apiSetting.apiToken}, thresholdIntensity=$thresholdIntensity")
          if (intensity > thresholdIntensity || maxIntensity > thresholdIntensity) {
            implicit val ec: ExecutionContextExecutor = materializer.executionContext
            val message =
              s"""${quake.occurrence_date} ${quake.occurrence_time}
                 |
                 |${quake.place_name} で 震度'${quake.intensity}', 最大震度'${quake.max_intensity}' の地震が発生しました。
                 |詳細は ${quake.url} を確認してください。
                 |
                 |身の回りの安全が確認できたら「完了」してください。
                 |完了をもって安否確認とします。""".stripMargin
            (for {
              me <- getMeRequest(GetMeRequest())
              members <- getMemberListRequest(GetMemberListRequest(roomId))
              ids <- createTaskRequest(CreateTaskRequest(roomId, message, None, members.withFilter(_.account_id != me.account_id).map(_.account_id)))
            } yield {
              ctx.log.info(message)
              ctx.log.info(s"Task assigned to ${ids.task_ids}")
            }).onComplete {
              case Failure(e) => ctx.log.error(e, "Failed chatwork api calling")
              case Success(_) => ()
            }
          }
        }

        implicit val ec: ExecutionContext = ctx.materializer.executionContext
        val f = for {
          body <- ctx.request.entity.dataBytes.runReduce(_ ++ _).map(_.utf8String)
          _ = ctx.log.info(ctx.request.headers.mkString("\n"))
          _ = ctx.log.info(body)
          rawThings = decode[YahooMyThings](body).fold[YahooMyThings](throw _, identity)
          things = removeTestWords(rawThings)
          _ = things.values.foreach(actionWhenEarthQuake)
        } yield ()

        onComplete(f) {
          case Success(_) =>
            complete(StatusCodes.OK)
          case Failure(e) =>
            ctx.log.error(e, "Error has occurred.")
            complete(StatusCodes.OK)
        }
      }
    }
  }

  def removeTestWords(myThings: YahooMyThings): YahooMyThings = {
    def eraseTest(s: String): String = s.replace("＜テスト実行＞", "")
    myThings.copy(values = myThings.values.map { quake =>
      quake.copy(
        place_name = eraseTest(quake.place_name),
        intensity = eraseTest(quake.intensity),
        max_intensity = eraseTest(quake.max_intensity),
        occurrence_date = eraseTest(quake.occurrence_date),
        occurrence_time = eraseTest(quake.occurrence_time),
        url = eraseTest(quake.url))
    })
  }

  val host = config.getString("n2.host")
  val port = config.getInt("n2.port")
  system.log.info(s"Starting server $host:$port")
  val serverBind = Http().bindAndHandle(route, host, port)
  sys.addShutdownHook({
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.duration._
    Await.result(serverBind.flatMap(_.unbind()).flatMap(_ => system.terminate()), 30.seconds)
  })
}
