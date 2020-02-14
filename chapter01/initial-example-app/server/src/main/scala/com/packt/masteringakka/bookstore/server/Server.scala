//#full-example
package com.packt.masteringakka.bookstore.server

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory

object Server {

  def main(args: Array[String]): Unit = {
    //    val greeterMain: ActorSystem[GreeterMain.SayHello] = ActorSystem(GreeterMain(), "Bookstore")
    //    greeterMain ! SayHello("Charles")
    val conf = ConfigFactory.load().getConfig("bookstore")
    ActorSystem[Nothing](Guardian(), "Bookstore", conf)
  }
}

object Guardian {

  def apply(): Behavior[Nothing] = {
    Behaviors.setup[Nothing](context => Behaviors.empty)
  }
}