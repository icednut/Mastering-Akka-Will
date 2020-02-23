package com.packt.masteringakka.bookstore.order

import akka.actor.typed.scaladsl.ActorContext
import com.packt.masteringakka.bookstore.common.{BookstorePlan, Bootstrap}

import scala.concurrent.ExecutionContext

class OrderBoot extends Bootstrap {

  def bootup(context: ActorContext[Nothing]): List[BookstorePlan] = {
    implicit val ec: ExecutionContext = context.executionContext
    val salesHandler = context.spawn(SalesOrderManager(), SalesOrderManager.Name)
    List(new SalesOrderEndpoint(salesHandler, context.system))
  }
}