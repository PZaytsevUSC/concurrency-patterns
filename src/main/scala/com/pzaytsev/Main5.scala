package com.pzaytsev
import scalaz._
import Scalaz._
import com.pzaytsev.Everything._
import scalaz.zio.{IO, Queue}

object Everything {

  case class MasterState(inProcess: Set[Int],
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
      case ComputeFactors(naturalNumber) =>
        val newState = state.copy(inProcess = state.inProcess + naturalNumber)
        delegateWork(state)
      case Factors(factors, num) =>
        val hash = num % 5
        val updatedState = state.copy(
          inProcess = state.inProcess - hash,
          processedNums = state.processedNums + (num -> factors))
        if (updatedState.inProcess.isEmpty) {
          IO.now(updatedState)
        } else {
          delegateWork(state)
        }

    }

  def delegateWork(state: MasterState): IO[Nothing, MasterState] = {
    // don't process anything is 5 concurrent factorizations are empty
    if (state.inProcess.size == 5 || state.inProcess.isEmpty) {
      IO.now(state)
    }

    val nextNaturalNumber = state.inProcess.head

    val newState = state.copy(inProcess = state.inProcess - nextNaturalNumber)

    for {
      (masterState, workerQueue) <- provideChannelForWorker(newState,
                                                            nextNaturalNumber)
      _ <- workerQueue.offer(nextNaturalNumber)
    } yield
      masterState.copy(
        inProcess = masterState.inProcess + nextNaturalNumber % 5)
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
      msg <- q.take // should return newState with empty processes to terminate
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
