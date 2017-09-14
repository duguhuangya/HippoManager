package com.cathay.dtag.hippo.manager.core

import java.io.File
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import com.cathay.dtag.hippo.manager.conf.{HippoConfig, HippoInstance}
import com.cathay.dtag.hippo.manager.conf.HippoConfig.CoordCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.EntryCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.HippoCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.Response._
import com.typesafe.config.ConfigFactory


object CoordApp extends App {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5 seconds)

  //val configPath = args(0)
  //val reporterConfig = ConfigFactory.parseFile(new File(configPath))
  val reporterConfig = ConfigFactory.parseFile(new File("config/reporter.conf"))
  val coordActor = Coordinator.initiate(2551, reporterConfig)

  Thread.sleep(11000)

  //val hConf = HippoConfig("edge1", "batchetl.journey", "/app/journey", checkInterval = 30*1000)
  val hConf = HippoConfig("edge1", "hippos.service.test1", "/Users/tykuo/Developer/main/projects/cathay/HippoPlugin/test")


  println("u can type hippo command: ")
  var ok = true
  while (ok) {
    val cmd = Console.readLine()
    ok = cmd != null

    cmd match {
      case msg if msg.startsWith("hippo register") => register()
      case msg if msg.startsWith("hippo show") => showStatus()
      case msg if msg.startsWith("hippo nodestatus") => coordActor ! PrintNodeStatus
      //case msg if msg.startsWith("hippo clusterstatus") => coordActor ! PrintClusterStatus

      case msg if msg.length > 11 && msg.startsWith("hippo start") =>
        val interval = msg.substring(12).toLong
        start(interval)

      case msg if msg.startsWith("hippo start") =>
        defaultStart()

      case msg if msg.startsWith("hippo stop") =>
        stop()

      case msg if msg.length > 13 && msg.startsWith("hippo restart") =>
        val interval = msg.substring(14).toLong
        restart(interval)

      case msg if msg.startsWith("hippo restart") =>
        defaultRestart()

      case _ => println("hippo command not found.")
    }



    def register() = {
      (coordActor ? Register(hConf)) onSuccess {
        case EntryCmdSuccess =>
          println("register successfully")
        case HippoExists =>
          println("HippoExists")
      }
    }

    def showStatus() = {
      (coordActor ? Operation(GetStatus, hConf.id)) onSuccess {
        case hi: HippoInstance =>
          println(hi)
        case HippoNotFound =>
          println("hippo not found.")
      }
    }

    def defaultStart() = {
      (coordActor ? Operation(Start(), hConf.id)) onSuccess {
        case StateCmdSuccess =>
          println(s"Start success, interval default 30 sec.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }

    def start(interval: Long) = {
      val msInterval = interval * 1000
      (coordActor ? Operation(Start(Some(msInterval)), hConf.id)) onSuccess {
        case StateCmdSuccess =>
          println(s"Start success, interval $interval sec.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }

    def stop() = {
      (coordActor ? Operation(Stop, hConf.id)) onSuccess {
        case StateCmdSuccess =>
          println("Stop success.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }

    def restart(interval: Long) = {
      val msInterval = interval * 1000
      (coordActor ? Operation(Restart(Some(msInterval)), hConf.id)) onSuccess {
        case StateCmdSuccess =>
          println(s"Restart success, interval $interval sec.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }

    def defaultRestart() = {
      (coordActor ? Operation(Restart(), hConf.id)) onSuccess {
        case StateCmdSuccess =>
          println("Restart success, interval default 30 sec.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }
  }
}
