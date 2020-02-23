package com.packt.masteringakka.bookstore.order

import java.util.Date

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.packt.masteringakka.bookstore.common._
import com.packt.masteringakka.bookstore.domain.book.{Book, BookEvent}
import com.packt.masteringakka.bookstore.domain.credit
import com.packt.masteringakka.bookstore.domain.credit.{ChargeCreditCard, CreditCardTransaction, CreditTransactionStatus}
import com.packt.masteringakka.bookstore.domain.order._
import com.packt.masteringakka.bookstore.domain.user.{BookstoreUser, UserEvent}
import slick.dbio.DBIOAction
import slick.jdbc.{GetResult, SQLActionBuilder}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author will.109
 * @date 2020/02/15
 **/
object SalesOrderManager extends ManagerActor {
  val Name = "order-manager"
  val BookMgrName: String = "book-manager"
  val UserManagerName: String = "user-manager"
  val CreditHandlerName: String = "credit-handler"
  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
  val CreditRejectedError = ErrorMessage("order.credit.rejected", Some("Your credit card has been rejected"))
  val InventoryNotAvailError = ErrorMessage("order.inventory.notavailable", Some("Inventory for an item on this order is no longer available"))

  def apply(): Behavior[OrderEvent] = {

    Behaviors.receive((context, message) => {
      implicit val timeout = Timeout(5.seconds)
      implicit val ec = context.executionContext
      implicit val scheduler = context.system.scheduler
      val dao = new SalesOrderManagerDao

      /**
       * Does a lookup of orders using information from the books tied to the orders
       *
       * @param f A function that returns a Future for the ids of the orders to lookup
       * @return a Future for a Vector of SalesOrder
       */
      def findForBook(f: => Future[Vector[Int]]) = {
        for {
          orderIds <- f
          orders <- dao.findOrdersByIds(orderIds.toSet)
        } yield orders
      }

      /**
       * Creates a new sales order in the system
       *
       * @param request The request to create the order
       * @return a Future for a SalesOrder that will be failed if any validation failures happen
       */
      def createOrder(request: CreateOrder): Future[SalesOrder] = {

        //Resolve dependencies in parallel
        val bookMgrFut: Option[ActorRef[BookEvent]] = lookup(BookMgrName)
        val userMgrFut: Option[ActorRef[UserEvent]] = lookup(UserManagerName)
        val creditMgrFut: Option[ActorRef[ChargeCreditCard]] = lookup(CreditHandlerName)

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

      /**
       * Calls over to the credit handler to charge the credit card
       *
       * @param request   The request to create the order
       * @param total     The total for the order
       * @param creditMgr The credit manager actor ref
       */
      def chargeCreditCard(request: CreateOrder, total: Double, creditMgr: ActorRef[ChargeCreditCard]) = {
        (creditMgr ? credit.ChargeCreditCard(request.cardInfo, total)).
          mapTo[ServiceResult[CreditCardTransaction]].
          flatMap(unwrapResult(ServiceResult.UnexpectedFailure)).
          flatMap {
            case txn if txn.status == CreditTransactionStatus.Approved =>
              Future.successful(txn)
            case txn =>
              Future.failed(new OrderProcessingException(CreditRejectedError))
          }
      }

      /**
       * Looks up books for each line item input and converts them into SalesOrderLineItems, vetting
       * if inventory is available for each first
       *
       * @param request The request to create the order
       * @param bookMgr The book manager actor ref
       * @return a Future for a list of SalesOrderLineItem that will be failed if validations fail
       */
      def buildLineItems(request: CreateOrder, bookMgr: ActorRef) = {
        //Lookup Books and map into SalesOrderLineItems, validating that inventory is available for each
        val quantityMap = request.lineItems.map(i => (i.bookId, i.quantity)).toMap

        Future.traverse(request.lineItems) { item =>
          (bookMgr ? FindBook(item.bookId)).
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

      /**
       * Takes a ServiceResult and, expecting it to be a FullResult, unwraps it to the underlying
       * type that the FullResult wraps.  If it's not a FullResult, the errorF is used to produce a failed
       * Future
       *
       * @param error  An error message that will be used to fail the future if it's not a FullResult
       * @param result The result to inspect and try and unwrap
       * @param A      Future for type T
       */
      def unwrapResult[T](error: ErrorMessage)(result: ServiceResult[T]): Future[T] = result match {
        case FullResult(user) => Future.successful(user)
        case other => Future.failed(new OrderProcessingException(error))
      }

      /**
       * Calls over to the userMgr to lookup a user by id
       *
       * @param request The request to create the order
       * @param userMgr The user manager actor ref
       * @return a Future wrapping a BookstoreUser that will be failed if the user does not exist
       */
      def loadUser(request: CreateOrder, userMgr: ActorRef) = {
        (userMgr ? FindUserById(request.userId)).
          mapTo[ServiceResult[BookstoreUser]].
          flatMap(unwrapResult(InvalidUserIdError))
      }

      /**
       * Looks up an actor ref via actor selection
       *
       * @param name The name of the actor to lookup
       * @return A Future for an ActorRef that will be failed if the actor does not exist
       */
      def lookup(name: String): Option[ActorRef[_]] = context.child(s"/user/$name")

      message match {
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
          val result = createOrder(req)
          pipeResponse(result.recover {
            case ex: OrderProcessingException => Failure(FailureType.Validation, ex.error)
          }, replyTo)
          Behaviors.same
      }
    })
  }

  class OrderProcessingException(val error: ErrorMessage) extends Throwable

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