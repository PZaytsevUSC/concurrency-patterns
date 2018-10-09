package com.pzaytsev.zio

import scalaz.zio._
import scalaz.zio.console._

/** Simple promise example
  *
  *
  */
object Main3 extends RTS {

  def main(args: Array[String]): Unit = {
    def sideEffectingAssigningValueProcess(
        p: Promise[Unit, String]): IO[Nothing, Boolean] = {
      Thread.sleep(5000)

      p.complete("Precious answer")
    }

    val program = for {
      p <- Promise.make[Unit, String]
      succeeded <- sideEffectingAssigningValueProcess(p)
      result <- succeeded match {
        case true  => p.get
        case false => IO.point("empty")
      }
      _ <- putStrLn(result)
    } yield ()

    unsafeRun(program)
  }

}
