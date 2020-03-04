package com.packt.masteringakka.bookstore.common

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext

/**
 * @author will.109
 * @date 2020/02/14
 **/
trait Bootstrap {

  def bootup(context: ActorContext[Nothing]): List[BookstorePlan]
}
