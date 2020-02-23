package com.packt.masteringakka.bookstore.book

import akka.actor.typed.scaladsl.ActorContext
import com.packt.masteringakka.bookstore.common.{BookstorePlan, Bootstrap}
import com.packt.masteringakka.bookstore.domain.book.BookDomain

/**
 * @author will.109
 * @date 2020/02/14
 **/
class BookBoot extends Bootstrap {

  override def bootup(context: ActorContext[Nothing]): List[BookstorePlan] = {
    val bookManager = context.spawn(BookManager(), BookDomain.Name)
    List(new BookEndpoint(bookManager, context.system))
  }
}
