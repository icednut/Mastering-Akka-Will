package com.packt.masteringakka.bookstore.domain.order

import java.util.Date

import akka.actor.typed.ActorRef
import com.packt.masteringakka.bookstore.domain.credit.CreditCardInfo

//Persistent entities
object SalesOrderStatus extends Enumeration{
  val InProgress, Shipped, Cancelled = Value
}
case class SalesOrder(id:Int, userId:Int, creditTxnId:Int, status:SalesOrderStatus.Value, totalCost:Double, lineItems:List[SalesOrderLineItem], createTs:Date, modifyTs:Date)
case class SalesOrderLineItem(id:Int, orderId:Int, bookId:Int, quantity:Int, cost:Double, createTs:Date,  modifyTs:Date)

//Lookup requests
sealed trait OrderEvent
case class FindOrderById(id:Int, replyTo: ActorRef[Nothing]) extends OrderEvent
case class FindOrdersForBook(bookId:Int, replyTo: ActorRef[Nothing]) extends OrderEvent
case class FindOrdersForUser(userId:Int, replyTo: ActorRef[Nothing]) extends OrderEvent
case class FindOrdersForBookTag(tag:String, replyTo: ActorRef[Nothing]) extends OrderEvent

//Create/Modify requests
case class LineItemRequest(bookId:Int, quantity:Int)
case class CreateOrder(userId:Int, lineItems:List[LineItemRequest], cardInfo:CreditCardInfo) extends OrderEvent
case class CreateOrderAndReply(createOrder: CreateOrder, replyTo: ActorRef[Nothing]) extends OrderEvent