package com.pzaytsev.zio

import scalaz.zio.console._
import scalaz.zio.{IO, Queue, RTS}

/**
  * This snippet demonstrates communication of systems via async channels. (Kinda like Akka actors)
  * Queue is async boundary, producer and consumer are writing to q reference.
  * Both processes run in fibers and join on completion.
  */
object Main2 extends App with RTS {
  def produce(left: List[String], q: Queue[String]): IO[Nothing, Unit] =
    left match {
      case head :: tail =>
        q.offer(head).flatMap(_ => produce(tail, q))
      case Nil => q.offer("")
    }

  def consume(q: Queue[String], acc: String): IO[Unit, String] =
    q.take.flatMap { result =>
      if (result.isEmpty) { IO.point(acc) } else {
        consume(q, acc + result)
      }
    }

  val program = for {
    exposure <- Queue.bounded[String](5)
    producer = produce(List("a", "b", "c", "d", "e", "f"), exposure)
    consumer = consume(exposure, "")
    prodFork <- producer.fork
    consumeFork <- consumer.fork
    _ <- prodFork.join
    acc <- consumeFork.join
    _ <- putStrLn(acc)
  } yield ()

  unsafeRun(program)

}
