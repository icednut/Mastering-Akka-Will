//#full-example
package com.packt.masteringakka.bookstore.server

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior}
import com.packt.masteringakka.bookstore.common.{BookstorePlan, Bootstrap, PostgresDb}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters

object Server {

  def main(args: Array[String]): Unit = {
    //    val greeterMain: ActorSystem[GreeterMain.SayHello] = ActorSystem(GreeterMain(), "Bookstore")
    //    greeterMain ! SayHello("Charles")
    val conf: Config = ConfigFactory.load().getConfig("bookstore")
    ActorSystem[Nothing](Guardian(conf), "Bookstore", conf)
  }
}

object Guardian {

  def apply(conf: Config): Behavior[Nothing] = {
    Behaviors.setup[Nothing](context => {
      PostgresDb.init(conf)
      startHttpServer(context, conf)
      Behaviors.empty
    })
  }

  private def startHttpServer(context: ActorContext[Nothing], conf: Config): Unit = {
    val endpoints: mutable.Seq[BookstorePlan] = CollectionConverters.asScala(conf.getStringList("serviceBoots"))
      .map(toBootClass)
      .flatMap(_.bootup(context))

    val server = endpoints.foldRight(unfiltered.netty.Server.http(8080)) {
      case (endpoint: BookstorePlan, serv) =>
        serv.plan(endpoint)
    }
    server.plan(PretentCreditCardService).run()
  }

  private def toBootClass(bootPrefix: String) = {
    val clazz = s"com.packt.masteringakka.bookstore.${bootPrefix.toLowerCase}.${bootPrefix}Boot"
    Class.forName(clazz).newInstance.asInstanceOf[Bootstrap]
  }
}