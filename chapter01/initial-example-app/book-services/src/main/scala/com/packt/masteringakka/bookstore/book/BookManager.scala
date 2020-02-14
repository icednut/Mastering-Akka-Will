package com.packt.masteringakka.bookstore.book

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.packt.masteringakka.bookstore.domain.book.BookEvent

/**
 * @author will.109
 * @date 2020/02/14
 **/
object BookManager {
  val Name = "book-manager"

  def apply(): Behavior[BookEvent] = {
    Behaviors.setup(context =>
      Behaviors.receiveMessage(message => {
        Behaviors.empty
      })
    )
  }

}
