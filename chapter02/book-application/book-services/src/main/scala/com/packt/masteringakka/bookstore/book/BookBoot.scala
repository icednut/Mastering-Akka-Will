package com.packt.masteringakka.bookstore.book

import akka.actor.typed.scaladsl.ActorContext
import com.packt.masteringakka.bookstore.common.{BookstorePlan, Bootstrap}

/**
 * @author will.109
 * @date 2020/02/14
 **/
class BookBoot extends Bootstrap {

  override def bootup(context: ActorContext[Nothing]): List[BookstorePlan] = {
    val bookManager = context.spawn(BookManager(), BookManager.Name)
    List(new BookEndpoint(bookManager, context.system))
  }
}
