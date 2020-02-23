package com.packt.masteringakka.bookstore.credit

import java.util.Date

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import com.packt.masteringakka.bookstore.common.{BookstoreDao, ManagerActor}
import com.packt.masteringakka.bookstore.domain.credit._
import dispatch.{Future, Http, as, url}
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext

/**
 * @author will.109
 * @date 2020/02/15
 **/
object CreditCardTransactionHandler extends ManagerActor {
  implicit val formats = Serialization.formats(NoTypeHints)

  def apply(): Behavior[CreditEvent] = {
    Behaviors.setup { context =>
      implicit val settings = CreditSettings(context.system)
      implicit val ec = context.executionContext

      val dao = new CreditCardTransactionHandlerDao
      context.system.receptionist ! Receptionist.Register(CreditDomain.CreditManagerKey, context.self)

      Behaviors.receiveMessage {
        case ChargeCreditCard(info, amount, replyTo) =>
          val result =
            for {
              chargeResp <- chargeCard(info, amount)
              txn = CreditCardTransaction(0, info, amount, CreditTransactionStatus.Approved, Some(chargeResp.confirmationCode), new Date, new Date)
              daoResult <- dao.createCreditTransaction(txn)
            } yield daoResult
          pipeResponse(result, replyTo)
          Behaviors.same
      }
    }
  }

  def chargeCard(info: CreditCardInfo, amount: Double)(implicit settings: CreditSettingsImpl, ec: ExecutionContext): Future[ChargeResponse] = {
    val jsonBody = write(ChargeRequest(info.cardHolder, info.cardType, info.cardNumber, info.expiration, amount))
    val request = url(settings.creditChargeUrl) << jsonBody
    Http.default(request OK as.String).map(read[ChargeResponse])
  }

  case class ChargeRequest(cardHolder: String, cardType: String, cardNumber: String, expiration: Date, amount: Double)

  case class ChargeResponse(confirmationCode: String)

}

class CreditCardTransactionHandlerDao(implicit ec: ExecutionContext) extends BookstoreDao {

  import DaoHelpers._

  /**
   * Creates a new credit card transaction record in the db
   *
   * @param txn The credit transaction to create
   * @return a Future wrapping that CreditCardTransaction with the id assigned
   */
  def createCreditTransaction(txn: CreditCardTransaction) = {
    val info = txn.cardInfo
    val insert =
      sqlu"""
      insert into CreditCardTransaction (cardHolder, cardType, cardNumber, expiration, amount, status, confirmationCode, createTs, modifyTs)
      values (${info.cardHolder}, ${info.cardType}, ${info.cardNumber}, ${info.expiration.toSqlDate}, ${txn.amount}, ${txn.status.toString}, ${txn.confirmationCode}, ${txn.createTs.toSqlDate}, ${txn.modifyTs.toSqlDate})
    """
    val getId = lastIdSelect("creditcardtransaction")
    db.run(insert.andThen(getId).withPinnedSession).map(v => txn.copy(id = v.headOption.getOrElse(0)))
  }

}
