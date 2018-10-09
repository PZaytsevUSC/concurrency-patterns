package com.pzaytsev.monix

import java.util.concurrent.TimeUnit

import com.pzaytsev.monix.AsyncExample.{performedTaskedCall, url}
import monix.execution.Scheduler.Implicits.global
import monix.execution.Scheduler
import monix.execution.FutureUtils

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import monix.eval.{Task, _}
import monix.execution.CancelableFuture
import monix.execution.atomic.{Atomic, AtomicAny}
import monix.reactive.Observable
import org.apache.http.HttpEntity
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.impl.client.HttpClients

import scala.collection.immutable.Queue
import scala.util.Try
import scala.concurrent.duration._
import scala.io.Source

object AsyncExample extends App {

  def perfromOldschoolHttpCall(
      url: String): Either[Throwable, CloseableHttpResponse] = {
    val httpGet = new HttpGet(url)
    val httpclient = HttpClients.createDefault
    val response = Try(httpclient.execute(httpGet))
    response.toEither
  }

  // use create to describe an async/sync compute with a callback
  def performedTaskedCall(url: String): Task[CloseableHttpResponse] = {
    Task.create { (scheduler, callback) =>
      val cancellable = scheduler.scheduleOnce(0.seconds) {
        perfromOldschoolHttpCall(url) match {
          case Right(resp)   => callback.onSuccess(resp)
          case Left(failure) => callback.onError(failure)
        }
      }
      cancellable
    }
  }

  def extractResultFromResponse(resp: CloseableHttpResponse): Task[String] = {
    Task.eval(
      Source
        .fromInputStream(resp.getEntity.getContent)
        .getLines()
        .mkString("\n"))
  }

  val url =
    "https://alvinalexander.com/source-code/scala/scala-http-post-client-example-java-uses-apache-httpclient"

  val compute = for {
    response <- performedTaskedCall(url)
    page <- extractResultFromResponse(response)
  } yield (page)

  compute.runAsync.foreach(println)

  Thread.sleep(5000)

}
