package com.pzaytsev
import scalaz._
import Scalaz._
import com.pzaytsev.Everything._
import scalaz.zio.{IO, Queue}

object Everything {

  case class MasterState(totalNumbers: Int,
                         inProcess: Set[Int],
                         processedNums: Map[Int, List[Int]],
                         workerToChannel: Map[Int, Queue[Int]],
                         incomingChannel: Queue[Int],
                         backRefQueue: Queue[Factors])

  sealed trait MasterMessage
  case class ComputeFactors(naturalNumber: Int) extends MasterMessage
  case class Factors(factors: List[Int], num: Int) extends MasterMessage

}

class Master() {

  def handle(msg: MasterMessage, state: MasterState): IO[Nothing, MasterState] =
    msg match {
      case ComputeFactors(naturalNumber) => delegateWork(state, naturalNumber)
//      case Factors(factors, num) =>
//        val hash = num % 5
//        val updatedState = state.copy(
//          inProcess = state.inProcess - hash,
//          processedNums = state.processedNums + (num -> factors))

    }

  def delegateWork(state: MasterState,
                   naturalNumber: Int): IO[Nothing, MasterState] = {

    if (state.inProcess.size == 5) { IO.now(state) } else
      for {
        (masterState, workerQueue) <- provideChannelForWorker(state,
                                                              naturalNumber)
        _ <- workerQueue.offer(naturalNumber)
      } yield
        masterState.copy(inProcess = masterState.inProcess + naturalNumber % 5)
  }

  def provideChannelForWorker(
      state: MasterState,
      num: Int): IO[Nothing, (MasterState, Queue[Int])] = {
    val toSendHash: Int = num % 5
    state.workerToChannel.get(toSendHash) match {
      case Some(queue) => IO.now((state, queue))
      case None =>
        for {
          q <- Queue.bounded[Int](3)
          _ <- new Worker().run(q, state.backRefQueue)

        } yield
          (state.copy(
             workerToChannel = state.workerToChannel + (toSendHash -> q)),
           q)
    }
  }

  def run(state: MasterState,
          q: Queue[Int]): IO[Nothing, Map[Int, List[Int]]] = {
    for {
      msg <- q.take
      newState <- handle(ComputeFactors(msg), state)
      e <- if (newState.inProcess.isEmpty) {
        IO.now(newState.processedNums)
      } else {
        run(newState, q)
      }

    } yield e
  }
}

class Worker {

  def computePrimeFactors(num: Int): List[Int] = {

    def divisible(i: Int, num: Int, factors: List[Int]): List[Int] = {
      if (num % i == 0) {
        divisible(i, num / i, factors :+ i)
      } else {
        factors
      }
    }

    def factorRecEven(num: Int, factors: List[Int]): List[Int] = {
      if (num % 2 == 0) factorRecEven(num / 2, factors :+ (num / 2))
      else factors
    }

    def factorRecOdd(acc: Int,
                     num: Int,
                     target: Int,
                     factors: List[Int]): List[Int] = {
      if (acc <= target)
        factorRecOdd(acc + 2, num, target, divisible(acc, num, factors))
      else factors

    }

    factorRecOdd(3,
                 num,
                 Math.sqrt(num).toInt,
                 factorRecEven(num, List.empty[Int]))
  }

  def processQMessage(q: Queue[Int]): IO[Nothing, (Int, List[Int])] = {
    for {
      number <- q.take
    } yield (number, computePrimeFactors(number))
  }

  def mainProcess(q: Queue[Int], backRef: Queue[Factors]): IO[Nothing, Unit] = {
    for {
      processFork <- processQMessage(q).fork
      (num, factors) <- processFork.join
      _ <- backRef
        .offer(Factors(factors, num))
        .fork
        .void // background reply to parent process
    } yield ()
  }

  def run(q: Queue[Int], backRef: Queue[Factors]) =
    mainProcess(q, backRef).forever

}
object Main5 {
  def main(args: Array[String]): Unit = {}
}
