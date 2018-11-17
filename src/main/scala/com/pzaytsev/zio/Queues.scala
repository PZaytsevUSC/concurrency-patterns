package com.pzaytsev.zio

import scalaz.zio.{IO, Queue, RTS, Schedule}

import scala.util.Random
import scala.concurrent.duration._
import scalaz.zio.console._
import scalaz.zio.interop.Task
import LocalMonads._

import scala.language.higherKinds
import cats.Monad
import cats.syntax.all._

/***
  * Async communication and tagless final.
  *
  *
  */
case class Command(command: String)

case class OtherCommand(command: Int)

trait QueueLike[F[_], A] {
  def consume(): F[A]
  def produce(a: A): F[Unit]
}

trait RandomCrapProducer[F[_]] {
  def run(): F[Unit]
  def no(): F[String]
}

class Service[F[_]](one: RandomCrapProducer[F], two: RandomCrapProducer[F])(
    implicit m: Monad[F]) {

  def run(): F[Unit] = {

    for {
      _ <- one.run()
      _ <- two.run()

    } yield ()
  }

  def hello(): F[String] = { println("here"); one.no() }
}

class Actual(e: EntityOne, e2: EnitityTwo) {

  def run(): Task[Unit] =
    for {
      _ <- e.run()
      _ <- e2.run()
    } yield ()
}

class EntityOne(q: Queue[Command], schedule: Schedule[Any, Int])
    extends RandomCrapProducer[Task] {

  private[this] def commandProducer(): Command = {
    val available = Array("up", "down", "left", "right")
    val rand = Random.nextInt(4)
    Command(available(rand))
  }

  private[this] def work(): Task[Unit] = {
    for {
      item <- IO.point(commandProducer())
      _ <- q.offer(item)
    } yield ()

  }

  def no(): Task[String] = {
    Task { "ooo" }
  }

  def run(): Task[Unit] =
    for {
      _ <- work().repeat(schedule).forever.fork
    } yield ()
}

class EnitityTwo(q: Queue[Command],
                 q2: Queue[OtherCommand],
                 schedule: Schedule[Any, Int])
    extends RandomCrapProducer[Task] {

  def no() = Task("ooo")

  private[this] def work(): Task[Unit] = {
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

  def run(): Task[Unit] = {
    for {
      _ <- work().repeat(schedule).forever.fork
    } yield ()
  }
}
object Queues extends App with RTS {

  val scheduler = Schedule.spaced(1000.milliseconds)
  def readFromQ(q: Queue[OtherCommand]): Task[Unit] = {
    for {
      command <- q.take
      _ <- putStrLn(command.command.toString)
    } yield ()
  }

  val program = for {
    q1 <- Queue.bounded[Command](5)
    q2 <- Queue.bounded[OtherCommand](5)
    entity1 = new EntityOne(q1, scheduler)
    entity2 = new EnitityTwo(q1, q2, scheduler)
    service = new Service(entity1, entity2)
    _ <- service.run()
    _ <- readFromQ(q2).repeat(scheduler).forever
  } yield ()

  unsafeRun(program)
}
