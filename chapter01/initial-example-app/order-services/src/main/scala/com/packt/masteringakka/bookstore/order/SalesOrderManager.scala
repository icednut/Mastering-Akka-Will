package com.packt.masteringakka.bookstore.order

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.packt.masteringakka.bookstore.domain.order.{CreateOrder, CreateOrderAndReply, FindOrderById, FindOrdersForBook, FindOrdersForBookTag, FindOrdersForUser, LineItemRequest, OrderEvent}

/**
 * @author will.109
 * @date 2020/02/15
 **/
object SalesOrderManager {
  val Name = "order-manager"

  def apply(): Behavior[OrderEvent] = {
    Behaviors.receive((context, message) => {
      message match {
        case FindOrderById(id, replyTo) =>
          Behaviors.same
        case FindOrdersForUser(userId, replyTo) =>
          Behaviors.same
        case FindOrdersForBook(bookId, replyTo) =>
          Behaviors.same
        case FindOrdersForBookTag(tag, replyTo) =>
          Behaviors.same
        case CreateOrderAndReply(CreateOrder(userId, lineItems, cardInfo), replyTo) =>
          Behaviors.same
      }
    })
  }
}
