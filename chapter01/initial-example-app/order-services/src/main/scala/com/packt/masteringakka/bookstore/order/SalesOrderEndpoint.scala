package com.packt.masteringakka.bookstore.order

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.packt.masteringakka.bookstore.common.BookstorePlan
import com.packt.masteringakka.bookstore.domain.order._
import io.netty.channel.ChannelHandler.Sharable
import unfiltered.request.{Seg, _}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Http endpoint class for sales order related actions
 */
@Sharable
class SalesOrderEndpoint(salesHandler: ActorRef[OrderEvent], system: ActorSystem[Nothing])(implicit val ec: ExecutionContext) extends BookstorePlan {

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler = system.scheduler

  def intent = {
    /**
     * 단건 주문(Order) 정보 가져오기 API
     */
    case req@GET(Path(Seg("api" :: "order" :: IntPathElement(id) :: Nil))) =>
      val f = salesHandler.ask(ref => FindOrderById(id, ref))
      respond(f, req)

    /**
     * userId에 해당하는 주문(Order) 정보 목록 가져오기 API
     */
    case req@GET(Path(Seg("api" :: "order" :: Nil))) & Params(UserIdParam(userId)) =>
      val f = salesHandler.ask(ref => FindOrdersForUser(userId, ref))
      respond(f, req)

    /**
     * bookId에 해당하는 주문(Order) 정보 목록 가져오기 API
     */
    case req@GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookIdParam(bookId)) =>
      val f = salesHandler.ask(ref => FindOrdersForBook(bookId, ref))
      respond(f, req)

    /**
     * bookTag에 해당하는 주문(Order) 정보 목록 가져오기 API
     */
    case req@GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookTagParam(tag)) =>
      val f = salesHandler.ask(ref => FindOrdersForBookTag(tag, ref))
      respond(f, req)

    /**
     * 주문 정보 등록하기
     */
    case req@POST(Path(Seg("api" :: "order" :: Nil))) =>
      val createReq = parseJson[CreateOrder](Body.string(req))
      val f = salesHandler.ask(ref => CreateOrderAndReply(createReq, ref))
      respond(f, req)
  }

  /** Unfilterd Param for the userId input for searching by userId */
  object UserIdParam extends Params.Extract("userId", Params.first ~> Params.int)

  /** Unfilterd Param for the bookId input for searching by bookId */
  object BookIdParam extends Params.Extract("bookId", Params.first ~> Params.int)

  /** Unfilterd Param for the bookTag input for searching by books by tag */
  object BookTagParam extends Params.Extract("bookTag", Params.first ~> Params.nonempty)

}