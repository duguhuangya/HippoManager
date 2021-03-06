package com.cathay.dtag.hippo.manager.api

import akka.actor.{ActorSelection, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.pattern.ask
import com.cathay.dtag.hippo.manager.core.schema.HippoConfig.EntryCommand
import com.cathay.dtag.hippo.manager.core.schema.{HippoConfig, HippoGroup, HippoInstance}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import spray.json._


trait APIRoute extends Directives with HippoJsonProtocol {
  import HippoConfig.CoordCommand._
  import HippoConfig.EntryCommand._
  import HippoConfig.HippoCommand._
  import HippoConfig.Response._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val ec: ExecutionContext
  implicit val timeout = Timeout(10 seconds)
  val version: String
  val coordAddress: String

  def coordinator: ActorSelection =
    system.actorSelection(s"$coordAddress/user/coordinator")

  def handleResponse(x: Any): Route = x match {
    case EntryCmdSuccess | StateCmdSuccess =>
      complete(StatusCodes.OK, JsObject(
        "message" -> JsString("Command deliver successfully.")
      ))
    case StateCmdException(reason) =>
      complete(StatusCodes.BadRequest, JsObject(
        "message" -> JsString("Command failed."),
        "reason" -> JsString(reason)
      ))
    case HippoExists =>
      complete(StatusCodes.BadRequest, JsObject(
        "message" -> JsString("Hippo exists.")
      ))
    case HippoNotFound =>
      complete(StatusCodes.NotFound, JsObject(
        "message" -> JsString("Hippo is not found.")
      ))
    case StateCmdUnhandled(currentState) =>
      complete(StatusCodes.BadRequest, JsObject(
        "message" -> JsString("Command can not be handled at this state."),
        "currentState" -> JsString(currentState)
      ))
  }

  def executeCommand(id: String, cmd: EntryCommand) = {
    val future = (coordinator ? cmd).flatMap {
      case EntryCmdSuccess | StateCmdSuccess =>
        coordinator ? Operation(GetStatus, id)
      case x =>
        Future(x)
    }

    onSuccess(future) {
      case instance: HippoInstance =>
        complete(instance)
      case x =>
        handleResponse(x)
    }
  }

  def commandRoute(id: String, interval: Option[Long]): Route =
    path("start") {
      // 4. Start hippo
      val cmd = Operation(Start(interval), id)
      executeCommand(id, cmd)
    } ~
    path("restart") {
      // 5. Restart hippo
      val cmd = Operation(Restart(interval), id)
      executeCommand(id, cmd)
    } ~
    path("stop") {
      // 6. Stop hippo
      val cmd = Operation(Stop, id)
      executeCommand(id, cmd)
    }

  def instanceRoute(id: String): Route = {
    /**
      * ### prefix
      *   1. /services/host/:host/name/:name
      *   2. /services/instances/:id
      */
    pathEnd {
      get {
        // 7. Get hippo status
        val op = Operation(GetStatus, id)
        onSuccess(coordinator ? op) {
          case instance: HippoInstance =>
            complete(instance)
          case x =>
            handleResponse(x)
        }
      } ~
      delete {
        // 8. Remove hippo
        onSuccess(coordinator ? Remove(id))(handleResponse)
      }
    }
  }

  def route =
    pathPrefix("hippo" / version / "services") {
      pathEnd {
        (post & entity(as[HippoConfig])) { config =>
          // 1. Register hippo
          onSuccess(coordinator ? Register(config)) {
            case EntryCmdSuccess =>
              complete(StatusCodes.Created, JsObject(
                "id" -> JsString(config.id),
                "coordAddress" -> JsString(this.coordAddress)
              ))
            case x =>
              handleResponse(x)
          }
        } ~
        (get & parameterMap) { params =>
          // 2. Get cluster status
          complete {
            (coordinator ? GetClusterStatus)
              .mapTo[Map[String, HippoGroup]]
              //.map(_.values)
              .map(_.values.map(_.filterByParams(params)))
          }
        }
      } ~
      path("node") {
        (get & parameterMap) { params =>
          // 3. Get node status
          complete {
            (coordinator ? GetNodeStatus(params)).mapTo[HippoGroup]
          }
        }
      } ~
      path("key") {
        (get & parameterMap) { params =>
          // 4. Get coordinator key
          complete{
            (coordinator ? GetSSHkey).mapTo[CoorRsaKey]
          }
          //val keyGen = new KeyGenerator
          //complete (JsObject("key" -> JsString(keyGen.getKey)))
        }
      } ~
//      pathPrefix("host" / Segment / "name" / Segment) { (host, name) =>
//        val id = HippoConfig.generateHippoID(host, name)
//        instanceRoute(id)
//      } ~
      pathPrefix("instances" / Segment) { id =>
        instanceRoute(id) ~
        (post & entity(as[JsValue])) { json =>
          val value = json.asJsObject.fields.get("interval")
          val interval = value.map(_.convertTo[Long])
          commandRoute(id, interval)
        }
      }
//      ~
//      (post & entity(as[CommandParams])) { cmdParams =>
//        val id = HippoConfig.generateHippoID(cmdParams.clientIP, cmdParams.path.get , cmdParams.serviceName)
//        commandRoute(id, cmdParams.interval)
//      }
    }
}
