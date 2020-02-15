package com.packt.masteringakka.bookstore.book

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.packt.masteringakka.bookstore.common.{FullResult, PipeResponse, ServiceResult}
import com.packt.masteringakka.bookstore.domain.book.{BookEvent, FindBook}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * @author will.109
 * @date 2020/02/14
 **/
object BookManager extends PipeResponse {
  val Name = "book-manager"

  def apply(): Behavior[BookEvent] = {
    Behaviors.setup(context =>
      Behaviors.receiveMessage[BookEvent] {
        case FindBook(id, replyTo) =>
          implicit val ec = context.executionContext
          context.log.info("Looking up book for id: {}", id)
          val originalFuture: Future[Int] = Future(id)
          pipeResponse(originalFuture, replyTo)
          Behaviors.same
      })
  }

}
