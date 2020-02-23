package com.packt.masteringakka.bookstore.credit

import akka.actor.typed.scaladsl.ActorContext
import com.packt.masteringakka.bookstore.common.Bootstrap
import com.packt.masteringakka.bookstore.domain.credit.CreditDomain

/**
 * @author will.109
 * @date 2020/02/15
 **/
class CreditBoot extends Bootstrap {
  def bootup(context: ActorContext[Nothing]) = {
    val creditActor = context.spawn(CreditCardTransactionHandler(), CreditDomain.Name)
    context.watch(creditActor)
    Nil
  }
}