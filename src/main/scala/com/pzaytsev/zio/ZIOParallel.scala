package com.pzaytsev.zio

import scalaz.zio.{IO, RTS}

object ZIOParallel extends App with RTS {

  def longAssComputingTask(list: List[Int]): Long = {
    list.sum
  }

  def computeThese(l1: List[Int],
                   l2: List[Int],
                   l3: List[Int]): IO[Nothing, Long] = {
    for {
      compute1 <- IO
        .point(longAssComputingTask(l1))
        .par(IO.point(longAssComputingTask(l2)))
        .par(IO.point(longAssComputingTask(l3)))
    } yield compute1._1._1 + compute1._1._2 + compute1._2
  }
  println(
    unsafeRun(
      computeThese(List.fill(1000000)(1),
                   List.fill(1000000)(2),
                   List.fill(10000000)(3))))
}
