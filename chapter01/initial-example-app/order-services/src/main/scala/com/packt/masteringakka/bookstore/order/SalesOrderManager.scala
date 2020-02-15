package com.packt.masteringakka.bookstore.order

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.packt.masteringakka.bookstore.common.BookstoreDao
import com.packt.masteringakka.bookstore.domain.order._
import slick.dbio.DBIOAction
import slick.jdbc.{GetResult, SQLActionBuilder}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author will.109
 * @date 2020/02/15
 **/
object SalesOrderManager {
  val Name = "order-manager"

  def apply(): Behavior[OrderEvent] = {
    Behaviors.receive((context, message) => {
      implicit val timeout = Timeout(5.seconds)
      implicit val ec = context.executionContext
      val dao = new SalesOrderManagerDao

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

object SalesOrderManagerDao {
  val BaseSelect = "select id, userId, creditTxnId, status, totalCost, createTs, modifyTs from SalesOrderHeader where"

  class InventoryNotAvailaleException extends Exception
  implicit val GetOrder = GetResult { r => SalesOrder(r.<<, r.<<, r.<<, SalesOrderStatus.withName(r.<<), r.<<, Nil, r.nextTimestamp, r.nextTimestamp) }
  implicit val GetLineItem = GetResult { r => SalesOrderLineItem(r.<<, r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp) }
}

class SalesOrderManagerDao(implicit ec: ExecutionContext) extends BookstoreDao {

  import DaoHelpers._
  import SalesOrderManagerDao._
  import slick.driver.PostgresDriver.api._

  /**
   * Finds a single order by id
   *
   * @param id The id of the order to find
   * @return a Future wrapping an optional SalesOrder
   */
  def findOrderById(id: Int) = findOrdersByIds(Set(id)).map(_.headOption)

  /**
   * Finds a Vector of orders by their ids
   *
   * @param ids The ids of the orders to find
   * @return a Future wrapping an Vector of SalesOrder
   */
  def findOrdersByIds(ids: Set[Int]) = {
    if (ids.isEmpty) Future.successful(Vector.empty)
    else {
      val idsInput = ids.mkString(",")
      val select = sql"#$BaseSelect id in (#$idsInput)"
      findOrdersByCriteria(select)
    }
  }

  /**
   * Uses a supplied select statement to find orders and the line items for those orders
   *
   * @param orderSelect a select that will return a Vector of SalesOrder
   * @return a Future wrapping a Vector of SalesOrder
   */
  private def findOrdersByCriteria(orderSelect: SQLActionBuilder) = {
    val headersF = db.run(orderSelect.as[SalesOrder])

    def selectItems(orderIds: Seq[Int]) = {
      if (orderIds.isEmpty) Future.successful(Vector.empty)
      else db.run(sql"select id, orderId, bookId, quantity, cost, createTs, modifyTs from SalesOrderLineItem where orderId in (#${orderIds.mkString(",")})".as[SalesOrderLineItem])
    }

    for {
      headers <- headersF
      items <- selectItems(headers.map(_.id))
    } yield {
      val itemsByOrder = items.groupBy(_.orderId)
      headers.map(o => o.copy(lineItems = itemsByOrder.get(o.id).map(_.toList).getOrElse(Nil)))
    }
  }

  /**
   * Finds orders tied to a specific user by id
   *
   * @param userId The id of the user to find orders for
   * @return a Future wrapping a Vector of SalesOrder
   */
  def findOrdersForUser(userId: Int) = {
    val select = sql"#$BaseSelect userId = $userId"
    findOrdersByCriteria(select)
  }

  /**
   * Finds orders ids that have a line item for the supplied book id
   *
   * @param bookId The id of the book to find orders for
   * @return a Future wrapping a Vector of Int order ods
   */
  def findOrderIdsForBook(bookId: Int) = {
    val select = sql"select distinct(orderId) from SalesOrderLineItem where bookId = $bookId"
    db.run(select.as[Int])
  }

  /**
   * Finds orders ids that have a line item for a book with the supplied tag
   *
   * @param tag The tag on the book to find orders for
   * @return a Future wrapping a Vector of Int order ids
   */
  def findOrderIdsForBookTag(tag: String) = {
    val select = sql"select distinct(l.orderId) from SalesOrderLineItem l right join BookTag t on l.bookId = t.bookId where t.tag = $tag"
    db.run(select.as[Int])
  }

  def createSalesOrder(order: SalesOrder) = {
    val insertHeader =
      sqlu"""
      insert into SalesOrderHeader (userId, creditTxnId, status, totalCost, createTs, modifyTs)
      values (${order.userId}, ${order.creditTxnId}, ${order.status.toString}, ${order.totalCost}, ${order.createTs.toSqlDate}, ${order.modifyTs.toSqlDate})
    """

    val getId = lastIdSelect("salesorderheader")

    def insertLineItems(orderId: Int) = order.lineItems.map { item =>
      val insert =
        sqlu"""
          insert into SalesOrderLineItem (orderId, bookId, quantity, cost, createTs, modifyTs)
          values ($orderId, ${item.bookId}, ${item.quantity}, ${item.cost}, ${item.createTs.toSqlDate}, ${item.modifyTs.toSqlDate})
        """

      //Using optimistic currency control on the update via the where clause
      val decrementInv =
        sqlu"""
          update Book set inventoryAmount = inventoryAmount - ${item.quantity} where id = ${item.bookId} and inventoryAmount >= ${item.quantity}
        """

      insert.
        andThen(decrementInv).
        filter(_ == 1)
    }


    val txn =
      for {
        _ <- insertHeader
        id <- getId
        if id.headOption.isDefined
        _ <- DBIOAction.sequence(insertLineItems(id.head))
      } yield {
        order.copy(id = id.head)
      }

    db.
      run(txn.transactionally).
      recoverWith {
        case ex: NoSuchElementException => Future.failed(new InventoryNotAvailaleException)
      }
  }
}