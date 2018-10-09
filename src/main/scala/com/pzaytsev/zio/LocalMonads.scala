package com.pzaytsev.zio

import scalaz._
import scalaz.zio._

object LocalMonads {
  implicit def IOMonad[E]: Monad[IO[E, ?]] = new Monad[IO[E, ?]] {
    override def point[A](a: => A): IO[E, A] = IO.point(a)

    override def bind[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] =
      fa flatMap f
  }
}
