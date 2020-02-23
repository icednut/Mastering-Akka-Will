package com.packt.masteringakka.bookstore.book

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.packt.masteringakka.bookstore.common.{BookstorePlan, ServiceResult}
import com.packt.masteringakka.bookstore.domain.book._
import io.netty.channel.ChannelHandler.Sharable
import unfiltered.netty.async.Plan.Intent
import unfiltered.request._
import unfiltered.response.Pass

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * @author will.109
 * @date 2020/02/14
 **/
@Sharable
class BookEndpoint(bookManager: ActorRef[BookEvent], system: ActorSystem[Nothing]) extends BookstorePlan {

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler = system.scheduler

  override implicit val ec: ExecutionContext = system.executionContext

  override def intent: Intent = {
    case req@GET(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: Nil))) =>
      val f: Future[ServiceResult[_]] = bookManager.ask((ref: ActorRef[ServiceResult[_]]) => FindBook(bookId, ref))
      respond(f, req)

    /**
     * Book TAG로 책 검색하기
     */
    case req@GET(Path(Seg("api" :: "book" :: Nil))) & Params(TagParam(tags)) =>
      val f = bookManager.ask((ref: ActorRef[ServiceResult[_]]) => FindBooksByTags(tags, ref))
      respond(f, req)

    /**
     * author로 책 검색하기
     */
    case req@GET(Path(Seg("api" :: "book" :: Nil))) & Params(AuthorParam(author)) =>
      val f = bookManager.ask((ref: ActorRef[ServiceResult[_]]) => FindBooksByAuthor(author, ref))
      respond(f, req)

    /**
     * 책 등록하기
     */
    case req@POST(Path(Seg("api" :: "book" :: Nil))) =>
      val createBook = parseJson[CreateBook](Body.string(req))
      val f = bookManager.ask((ref: ActorRef[ServiceResult[_]]) => CreateBookAndReply(createBook, ref))
      respond(f, req)

    /**
     * 책에 태그 등록하거나 삭제하기
     */
    case req@Path(Seg("api" :: "book" :: IntPathElement(bookId) :: "tag" :: tag :: Nil)) =>
      req match {
        case PUT(_) =>
          val f = bookManager.ask((ref: ActorRef[ServiceResult[_]]) => AddTagToBook(bookId, tag, ref))
          respond(f, req)
        case DELETE(_) =>
          val f = bookManager.ask((ref: ActorRef[ServiceResult[_]]) => RemoveTagFromBook(bookId, tag, ref))
          respond(f, req)
        case other =>
          req.respond(Pass)
      }

    /**
     * 인벤토리에 책 넣기
     */
    case req@PUT(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: "inventory" :: IntPathElement(amount) :: Nil))) =>
      val f = bookManager.ask((ref: ActorRef[ServiceResult[_]]) => AddInventoryToBook(bookId, amount, ref))
      respond(f, req)

    /**
     * 책 삭제하기
     */
    case req@DELETE(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: Nil))) =>
      val f = bookManager.ask((ref: ActorRef[ServiceResult[_]]) => DeleteBook(bookId, ref))
      respond(f, req)
  }

  /**
   * Unfiltered param for handling the multi value tag param
   */
  object TagParam extends Params.Extract("tag", { values =>
    val filtered = values.filter(_.nonEmpty)
    if (filtered.isEmpty) None else Some(filtered)
  })

  /** Unfiltered param for the author param */
  object AuthorParam extends Params.Extract("author", Params.first ~> Params.nonempty)


}
