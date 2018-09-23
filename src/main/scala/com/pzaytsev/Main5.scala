package com.pzaytsev
import java.io.IOException

import scalaz._
import Scalaz._
import com.pzaytsev.Everything._
import scalaz.zio.{IO, Queue, RTS}
import scalaz.zio.console._
import LocalMonads._

/**
  *
  * Master - worker pattern via ZIO.
  * Similar to routing in akka.
  * Master receives a natural number to factor via async channel. Delegates work to one of the 5 workers via their channels.
  * Master receives a response in the background, aggregates the results.
  * Communication channels are typed and state is handled in a functional way.
  *
  */
object Everything {

  case class MasterState(toProcess: Set[Int],
                         inProcess: Set[Int],
                         processedNums: Map[Int, List[Int]],
                         workerToChannel: Map[Int, Queue[Int]])

  sealed trait MasterMessage
  case class ComputeFactors(naturalNumber: Int) extends MasterMessage
  case class Factors(factors: List[Int], num: Int) extends MasterMessage

}

class Master {

  def combineStates(state: MasterState, num2: Int)(
      implicit q: Queue[MasterMessage]): IO[Nothing, MasterState] = {
    val newState = state.copy(toProcess = state.toProcess + num2)
    delegateWork(newState, q)
  }
  def handle(msg: MasterMessage,
             state: MasterState,
             q: Queue[MasterMessage]): IO[Nothing, MasterState] =
    msg match {

      // creates work
      case ComputeFactors(num) =>
        delegateWork(state.copy(toProcess = state.toProcess + num), q)
      // processes work
      case Factors(factors, num) =>
        val updatedState = state.copy(
          inProcess = state.inProcess - num,
          processedNums = state.processedNums + (num -> factors))
        delegateWork(updatedState, q)

    }

  def delegateWork(state: MasterState,
                   q: Queue[MasterMessage]): IO[Nothing, MasterState] = {

    if (state.toProcess.isEmpty) {

      return IO.now(state)
    }

    val nextNaturalNumber = state.toProcess.head
    val newState = state.copy(toProcess = state.toProcess - nextNaturalNumber)

    for {

      stateAndQueue <- provideChannelForWorker(newState, nextNaturalNumber, q)
      state2 = stateAndQueue._1
      workerQueue = stateAndQueue._2

      _ <- workerQueue.offer(nextNaturalNumber)
    } yield state2.copy(inProcess = state2.inProcess + nextNaturalNumber)
  }

  def provideChannelForWorker(state: MasterState,
                              num: Int,
                              incoming: Queue[MasterMessage])
    : IO[Nothing, (MasterState, Queue[Int])] = {
    val toSendHash: Int = num % 5

    state.workerToChannel.get(toSendHash) match {
      case Some(queue) => IO.now((state, queue))
      case None =>
        for {
          q <- Queue.bounded[Int](3)
          _ <- new Worker().run(q, incoming).fork

        } yield
          (state.copy(
             workerToChannel = state.workerToChannel + (toSendHash -> q)),
           q)
    }
  }

  def run(state: MasterState,
          q: Queue[MasterMessage]): IO[IOException, Map[Int, List[Int]]] = {

    for {

      msg <- q.take
      newState <- handle(msg, state, q)
      e <- if (newState.toProcess.isEmpty && newState.inProcess.isEmpty) {

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

    def factorRecEven(num: Int, factors: List[Int]): (Int, List[Int]) = {
      if (num % 2 == 0) factorRecEven(num / 2, factors :+ 2)
      else (num, factors)
    }

    def factorRecOdd(acc: Int,
                     num: Int,
                     target: Int,
                     factors: List[Int]): List[Int] = {
      if (acc <= target) {
        factorRecOdd(acc + 2, num, target, divisible(acc, num, factors))
      } else {
        if (num > 2) num :: factors else factors
      }

    }

    val (newNum, list) = factorRecEven(num, List.empty[Int])

    factorRecOdd(3, newNum, Math.sqrt(num).toInt, list)
  }

  def processQMessage(q: Queue[Int]): IO[Nothing, (Int, List[Int])] = {

    for {
      number <- q.take
    } yield (number, computePrimeFactors(number))
  }

  def mainProcess(q: Queue[Int],
                  backRef: Queue[MasterMessage]): IO[Nothing, Unit] = {
    for {
      processFork <- processQMessage(q).fork
      numAndFactors <- processFork.join
      _ <- backRef
        .offer(Factors(numAndFactors._2, numAndFactors._1))
        .fork
        .void // background reply to parent process
    } yield ()
  }

  def run(q: Queue[Int],
          backRef: Queue[MasterMessage]): IO[Nothing, Nothing] = {

    mainProcess(q, backRef).forever
  }

}
object Main5 extends RTS {
  def main(args: Array[String]): Unit = {

    val program = for {
      channel <- Queue.bounded[MasterMessage](3)
      worker = new Master()
      masterState = MasterState(Set.empty[Int],
                                Set.empty[Int],
                                Map.empty[Int, List[Int]],
                                Map.empty[Int, Queue[Int]])

      _ <- channel.offer(ComputeFactors(315))
      _ <- channel.offer(ComputeFactors(780))
      _ <- channel.offer(ComputeFactors(254))

      mapOfFactors <- worker.run(masterState, channel)
      _ <- putStrLn(mapOfFactors.toString())
    } yield ()

    val e = IO.supervise(program)
    unsafeRun(e)
  }

}
