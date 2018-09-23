package com.pzaytsev

import java.io.IOException

import scalaz.zio.{IO, RTS, Ref, Schedule}
import scalaz.zio.console._
import scala.concurrent.duration._
import scalaz._
import Scalaz._

/**
  *
  * Conditional communication channel.
  * Processes compete for stdout while coordinating writes and reads through cond var.
  * The order of commands is preserved because reads and writes are atomic.
  */
class Performer {
  def perform(ref: Ref[String]): IO[IOException, Unit] = {

    val result: IO[IOException, String] = for {
      cond <- ref.get
      response <- cond match {
        case "up"   => IO.point("jump")
        case "down" => IO.point("sit")
        case "stop" => IO.point("stop")
        case _      => IO.point("...")
      }
      _ <- putStrLn(response + "?").delay(1 second)

    } yield (response)

    result.flatMap {
      case "stop" => IO.point()
      case _      => perform(ref)

    }

  }

}

class Manager {
  def command(listOfCommands: List[String],
              ref: Ref[String]): IO[IOException, Unit] = listOfCommands match {
    case head :: tail =>
      for {
        _ <- putStrLn(head + "!").delay(1 second)
        answer <- ref.set(head).flatMap(_ => command(tail, ref))
      } yield answer
    case Nil => IO.point()
  }
}

object Main4 extends RTS {

  def main(args: Array[String]): Unit = {

    val program = for {
      condVar <- Ref[String]("")
      manager = new Manager
      artist = new Performer
      managerFiber <- manager
        .command(
          List("up", "up", "up", "down", "left", "right", "left", "stop"),
          condVar)
        .fork
      artistFiber <- artist.perform(condVar).fork
      _ <- artistFiber.join
      _ <- managerFiber.join
    } yield ()

    unsafeRun(program)

  }
}
