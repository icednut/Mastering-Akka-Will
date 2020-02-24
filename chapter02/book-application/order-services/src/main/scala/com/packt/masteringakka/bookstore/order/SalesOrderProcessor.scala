package com.packt.masteringakka.bookstore.order

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.packt.masteringakka.bookstore.common.{HttpResponseMixin, ServiceResult}
import com.packt.masteringakka.bookstore.domain.book.{Book, BookDomain, BookEvent}
import com.packt.masteringakka.bookstore.domain.credit.{CreditDomain, CreditEvent}
import com.packt.masteringakka.bookstore.domain.order.CreateOrder
import com.packt.masteringakka.bookstore.domain.user.{BookstoreUser, UserDomain, UserEvent}
import com.packt.masteringakka.bookstore.order.SalesOrderProcessor.SalesOrderEvent

/**
 * @author will.109
 * @date 2020/02/23
 **/
object SalesOrderProcessor extends HttpResponseMixin {

  def apply(): Behavior[SalesOrderEvent] = {
    idle(UnresolvedDependencies())
  }

  private def idle(data: UnresolvedDependencies,
                   req: CreateOrder = null,
                   replyTo: ActorRef[ServiceResult[_]] = null): Behavior[SalesOrderEvent] = {
    Behaviors.setup { context =>
      implicit val ec = context.executionContext
      val actorIdentifyResponseAdapter = context.messageAdapter[Receptionist.Listing](ActorIdentifyResponse)

      Behaviors.receiveMessage {
        case CreateOrderForward(req: CreateOrder, replyTo: ActorRef[ServiceResult[_]]) =>
          context.system.receptionist ! Receptionist.Find(UserDomain.UserManagerKey, actorIdentifyResponseAdapter)
          idle(data, req, replyTo)
        case ActorIdentifyResponse(listing) =>
          val newData: UnresolvedDependencies = listing.getKey match {
            case UserDomain.UserManagerKey =>
              data.copy(userManager = listing.serviceInstances(UserDomain.UserManagerKey).headOption)
            case BookDomain.BookManagerKey =>
              data.copy(bookManager = listing.serviceInstances(BookDomain.BookManagerKey).headOption)
            case CreditDomain.CreditManagerKey =>
              data.copy(creditManager = listing.serviceInstances(CreditDomain.CreditManagerKey).headOption)
          }
          newData match {
            case UnresolvedDependencies(Some(userManager), Some(bookManager), Some(_)) =>
              // TODO: 유저와 북을 찾아서 다음 상태로 넘어가자.
              val resolvedDependencies = ???
              lookingUpEntities(resolvedDependencies, req, replyTo)
            case _ => idle(newData, req, replyTo)
          }
      }
    }
  }

  private def lookingUpEntities(data: ResolvedDependencies,
                                req: CreateOrder,
                                replyTo: ActorRef[ServiceResult[_]]): Behavior[SalesOrderEvent] = {
    Behaviors.receive {(context, message) =>
      Behaviors.same
    }
  }

  sealed trait SalesOrderEvent

  case class CreateOrderForward(createOrder: CreateOrder, replyTo: ActorRef[ServiceResult[_]]) extends SalesOrderEvent

  private case class ActorIdentifyResponse(listing: Receptionist.Listing) extends SalesOrderEvent

  private case class UnresolvedDependencies(userManager: Option[ActorRef[UserEvent]] = None,
                                            bookManager: Option[ActorRef[BookEvent]] = None,
                                            creditManager: Option[ActorRef[CreditEvent]] = None)

  private case class ResolvedDependencies(book: Book, user: BookstoreUser)

}
