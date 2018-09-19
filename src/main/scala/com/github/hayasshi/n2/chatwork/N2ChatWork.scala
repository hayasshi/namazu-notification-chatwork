package com.github.hayasshi.n2.chatwork

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.concurrent.{ Await, ExecutionContext }
import scala.util.{ Failure, Success }

object N2ChatWork extends App {

  implicit val system: ActorSystem = ActorSystem("n2chatwork")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val config = system.settings.config

  val roomId = system.settings.config.getInt("n2.chatwork.target-room-id")
  val thresholdIntensity = system.settings.config.getInt("n2.chatwork.threshold-intensity")

  import io.circe.generic.auto._
  import io.circe.parser._
  val route = path("earthquake") {
    post {
      extractRequestContext { ctx =>

        def actionWhenEarthQuake(quake: YahooEarthQuake): Unit = {
          ctx.log.info(s"${quake.occurrence_date} ${quake.occurrence_time} ${quake.place_name} で 震度'${quake.intensity}', 最大震度'${quake.max_intensity}' の地震が発生しました。詳細は ${quake.url} を確認してください。")
        }

        implicit val ec: ExecutionContext = ctx.materializer.executionContext
        val f = for {
          body <- ctx.request.entity.dataBytes.runReduce(_ ++ _).map(_.utf8String)
          things = decode[YahooMyThings](body).fold[YahooMyThings](throw _, identity)
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
