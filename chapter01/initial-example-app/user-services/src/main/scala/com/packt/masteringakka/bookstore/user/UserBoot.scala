package com.packt.masteringakka.bookstore.user

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import com.packt.masteringakka.bookstore.common.{BookstorePlan, Bootstrap}
import com.packt.masteringakka.bookstore.domain.user.UserEvent

class UserBoot extends Bootstrap {
  def bootup(context: ActorContext[Nothing]): List[BookstorePlan] = {
    implicit val ec = context.executionContext
    val userManager: ActorRef[UserEvent] = context.spawn(UserManager(), UserManager.Name)
    List(new UserEndpoint(userManager, context.system))
  }
}