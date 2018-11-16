package com.pzaytsev.zio

import scalaz.zio.{IO, Queue, RTS, Schedule}

import scala.util.Random
import scala.concurrent.duration._
import scalaz.zio.console._
case class Command(command: String)

case class OtherCommand(command: Int)

class EntityOne(q: Queue[Command]) {

  def commandProducer(): Command = {
    val available = Array("up", "down", "left", "right")
    val rand = Random.nextInt(4)
    Command(available(rand))
  }

  def run(): IO[Any, Unit] = {

    for {
      item <- IO.point(commandProducer())
      _ <- q.offer(item)
    } yield ()
  }
}

class EnitityTwo(q: Queue[Command], q2: Queue[OtherCommand]) {
  def run(): IO[Any, Unit] = {
    for {
      elem <- q.take
      transform <- IO.point(
        if (elem.command == "up") OtherCommand(1)
        else if (elem.command == "down") OtherCommand(2)
        else if (elem.command == "left") OtherCommand(3)
        else OtherCommand(4))
      _ <- q2.offer(transform)
    } yield ()
  }
}
object Queues extends App with RTS {
  val forever = Schedule.spaced(1000.milliseconds)

  val program: IO[Any, Unit] = for {
    queue <- Queue.bounded[Command](5)
    intQueue <- Queue.bounded[OtherCommand](5)
    entity = new EntityOne(queue)
    entity2 = new EnitityTwo(queue, intQueue)
    _ <- entity.run().repeat(forever).fork
    _ <- entity2.run().repeat(forever).fork
    elem <- intQueue.take
    _ <- putStrLn(elem.command.toString)
  } yield ()

  unsafeRun(program.repeat(forever))
}
