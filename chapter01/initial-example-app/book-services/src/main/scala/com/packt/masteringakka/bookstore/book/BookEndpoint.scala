package com.packt.masteringakka.bookstore.book

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.packt.masteringakka.bookstore.common.BookstorePlan
import com.packt.masteringakka.bookstore.domain.book.{BookEvent, FindBook}
import unfiltered.netty.async.Plan.Intent
import unfiltered.request.{GET, Path, Seg}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author will.109
 * @date 2020/02/14
 **/
class BookEndpoint(bookManager: ActorRef[BookEvent], system: ActorSystem[Nothing]) extends BookstorePlan {

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler = system.scheduler

  override def intent: Intent = {
    case req@GET(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: Nil))) => {
      val f: Future[Any] = bookManager.ask(ref => FindBook(bookId))
      respond(f, req)
    }
  }

  override implicit val ec: ExecutionContext = system.executionContext
}
