package com.packt.masteringakka.bookstore.domain.credit

import java.util.Date

import akka.actor.typed.ActorRef
import com.packt.masteringakka.bookstore.common.ServiceResult

//Persistent entities
object CreditTransactionStatus extends Enumeration {
  val Approved, Rejected = Value
}

case class CreditCardInfo(cardHolder: String, cardType: String, cardNumber: String, expiration: Date)

case class CreditCardTransaction(id: Int, cardInfo: CreditCardInfo, amount: Double, status: CreditTransactionStatus.Value, confirmationCode: Option[String], createTs: Date, modifyTs: Date)

//Create/Modify requests
case class ChargeCreditCard(cardInfo: CreditCardInfo, amount: Double, actorRef: ActorRef[ServiceResult[_]])