package com.pzaytsev

import java.io.File

import org.apache.commons.io.monitor.{
  FileAlterationListenerAdaptor,
  FileAlterationMonitor,
  FileAlterationObserver
}

import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.zio
import scalaz.zio.{Callback, ExitResult, IO, RTS}
import scalaz.zio.console._

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/***
  * This snippet demonstrates how to bridge the gap between legacy callback based systems and ZIO.
  * Complete a promise and extract future ~> wrap result of a future callback in ZIO Callback
  * Fork this compute in fiber ~> let the main thread perform some compute then join a fiber when result is ready
  */
object Main extends App with RTS {

  def fileCreated(dir: String): Future[String] = {

    val p = Promise[String]
    val mon = new FileAlterationMonitor(1000)
    val obs = new FileAlterationObserver(dir)
    val adapter = new FileAlterationListenerAdaptor {
      override def onFileCreate(file: File): Unit = {
        p.trySuccess(file.getName)
      }
    }

    obs.addListener(adapter)
    mon.addObserver(obs)
    mon.start()
    p.future
  }

  val success: Future[String] = fileCreated(".")

  val e: IO[Throwable, String] = IO.async { cb: Callback[Throwable, String] =>
    success.onComplete {
      case Success(value) => cb(ExitResult.Completed(value))
      case Failure(ex)    => cb(ExitResult.Failed(ex))
    }
  }

  val p = for {
    produced <- e.fork
    compute <- IO.point(2 + 2)
    _ <- putStrLn(compute.toString)
    production <- produced.join
    _ <- putStrLn(production)

  } yield ()

  // create some file
  val m = unsafeRun(p)

}
