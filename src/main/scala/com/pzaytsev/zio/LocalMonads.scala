package com.pzaytsev.zio

import scalaz._
import scalaz.zio._
import scalaz.zio.interop.Task
import cats.{Monad => CatMonad}
import cats.syntax.all._
object LocalMonads {
  implicit def IOMonad[E]: Monad[IO[E, ?]] = new Monad[IO[E, ?]] {
    override def point[A](a: => A): IO[E, A] = IO.point(a)

    override def bind[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] =
      fa flatMap f
  }

  implicit def TaskMonad: CatMonad[Task] = new CatMonad[Task] {

    override def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] =
      fa flatMap f

    // using cats because need a tail recursive monad instance
    override def tailRecM[A, B](a: A)(f: A => Task[Either[A, B]]): Task[B] = {
      for {
        either <- f(a)
        result <- either match {
          case Right(e) => point(e)
          case Left(b)  => tailRecM(b)(f)
        }
      } yield result
    }

    override def pure[A](x: A): Task[A] = Task.point(x)
  }
}
