package com.packt.masteringakka.bookstore.order

import java.util.Date

import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.packt.masteringakka.bookstore.common._
import com.packt.masteringakka.bookstore.domain.book.{Book, BookDomain, BookEvent, FindBook}
import com.packt.masteringakka.bookstore.domain.credit
import com.packt.masteringakka.bookstore.domain.credit._
import com.packt.masteringakka.bookstore.domain.order._
import com.packt.masteringakka.bookstore.domain.user.{BookstoreUser, FindUserById, UserDomain, UserEvent}
import com.packt.masteringakka.bookstore.order.SalesOrderProcessor.CreateOrderForward
import slick.dbio.DBIOAction
import slick.jdbc.{GetResult, SQLActionBuilder}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/**
 * @author will.109
 * @date 2020/02/15
 **/
object SalesOrderManager extends HttpResponseMixin {
  val Name = "order-manager"
  val BookMgrName: String = "book-manager"
  val UserManagerName: String = "user-manager"
  val CreditHandlerName: String = "credit-handler"
  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
  val CreditRejectedError = ErrorMessage("order.credit.rejected", Some("Your credit card has been rejected"))
  val InventoryNotAvailError = ErrorMessage("order.inventory.notavailable", Some("Inventory for an item on this order is no longer available"))

  def apply(): Behavior[OrderEvent] = {
    Behaviors.setup { context =>
      implicit val timeout = Timeout(5.seconds)
      implicit val ec = context.executionContext
      implicit val scheduler = context.system.scheduler
      val dao = new SalesOrderManagerDao

      def createOrder(request: CreateOrder): Future[SalesOrder] = {
        val bookMgrFut: Future[ActorRef[BookEvent]] = lookup[BookEvent](BookDomain.BookManagerKey)
        val userMgrFut: Future[ActorRef[UserEvent]] = lookup[UserEvent](UserDomain.UserManagerKey)
        val creditMgrFut: Future[ActorRef[ChargeCreditCard]] = lookup[CreditEvent](CreditDomain.CreditManagerKey)

        for {
          bookMgr <- bookMgrFut
          userMgr <- userMgrFut
          creditMgr <- creditMgrFut
          (user, lineItems) <- loadUser(request, userMgr).zip(buildLineItems(request, bookMgr))
          total = lineItems.map(_.cost).sum
          creditTxn <- chargeCreditCard(request, total, creditMgr)
          order = SalesOrder(0, user.id, creditTxn.id, SalesOrderStatus.InProgress, total, lineItems, new Date, new Date)
          daoResult <- dao.createSalesOrder(order)
        } yield daoResult

      }

      def findForBook(f: => Future[Vector[Int]]) = {
        for {
          orderIds <- f
          orders <- dao.findOrdersByIds(orderIds.toSet)
        } yield orders
      }

      def chargeCreditCard(request: CreateOrder, total: Double, creditMgr: ActorRef[ChargeCreditCard]) = {
        creditMgr.ask((ref: ActorRef[ServiceResult[_]]) => credit.ChargeCreditCard(request.cardInfo, total, ref)).
          mapTo[ServiceResult[CreditCardTransaction]].
          flatMap(unwrapResult(ServiceResult.UnexpectedFailure)).
          flatMap {
            case txn if txn.status == CreditTransactionStatus.Approved =>
              Future.successful(txn)
            case txn =>
              Future.failed(new OrderProcessingException(CreditRejectedError))
          }
      }

      def buildLineItems(request: CreateOrder, bookMgr: ActorRef[BookEvent]) = {
        //Lookup Books and map into SalesOrderLineItems, validating that inventory is available for each
        val quantityMap = request.lineItems.map(i => (i.bookId, i.quantity)).toMap

        Future.traverse(request.lineItems) { item =>
          bookMgr.ask((ref: ActorRef[ServiceResult[_]]) => FindBook(item.bookId, ref)).
            mapTo[ServiceResult[Book]].
            flatMap(unwrapResult(InvalidBookIdError))
        }.
          flatMap { books =>
            val inventoryAvail = books.forall { b =>
              quantityMap.get(b.id).map(q => b.inventoryAmount >= q).getOrElse(false)
            }
            if (inventoryAvail)
              Future.successful(books.map { b =>
                val quantity = quantityMap.getOrElse(b.id, 0) //safe as we already vetted in the above step
                SalesOrderLineItem(0, 0, b.id, quantity, quantity * b.cost, new Date, new Date)
              })
            else
              Future.failed(new OrderProcessingException(InventoryNotAvailError))
          }
      }

      def unwrapResult[T](error: ErrorMessage)(result: ServiceResult[T]): Future[T] = result match {
        case FullResult(user) => Future.successful(user)
        case other => Future.failed(new OrderProcessingException(error))
      }

      def loadUser(request: CreateOrder, userMgr: ActorRef[UserEvent]) = {
        userMgr.ask((ref: ActorRef[ServiceResult[_]]) => FindUserById(request.userId, ref)).
          mapTo[ServiceResult[BookstoreUser]].
          flatMap(unwrapResult(InvalidUserIdError))
      }

      def lookup[T](serviceKey: ServiceKey[T]): Future[ActorRef[T]] = {
        val eventualListing: Future[Listing] = context.system.receptionist.ask(Receptionist.Find(serviceKey))
        eventualListing
          .map(each => each.getServiceInstances(serviceKey).asScala)
          .map(each => each.head)
      }

      Behaviors.receiveMessage {
        case FindOrderById(id, replyTo) =>
          pipeResponse(dao.findOrderById(id), replyTo)
          Behaviors.same
        case FindOrdersForUser(userId, replyTo) =>
          pipeResponse(dao.findOrdersForUser(userId), replyTo)
          Behaviors.same
        case FindOrdersForBook(bookId, replyTo) =>
          val result = findForBook(dao.findOrderIdsForBook(bookId))
          pipeResponse(result, replyTo)
          Behaviors.same
        case FindOrdersForBookTag(tag, replyTo) =>
          val result = findForBook(dao.findOrderIdsForBookTag(tag))
          pipeResponse(result, replyTo)
          Behaviors.same
        case CreateOrderAndReply(req, replyTo) =>
          context.log.info("Creating new sales order processor and forwarding request")
          //          val result = createOrder(req)
          //          pipeResponse(result.recover {
          //            case ex: OrderProcessingException => Failure(FailureType.Validation, ex.error)
          //          }, replyTo)
          val salesOrderProcessor = context.spawnAnonymous(SalesOrderProcessor())
          salesOrderProcessor ! CreateOrderForward(req, replyTo)
          Behaviors.same
      }
    }
  }

  class OrderProcessingException(val error: ErrorMessage) extends Throwable

  private case class ListingResponse(listing: Receptionist.Listing) extends OrderEvent

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