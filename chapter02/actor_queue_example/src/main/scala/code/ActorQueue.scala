package code

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import code.ShutdownReaper.Monitor

object ActorQueue {

  def apply(): Behavior[Command] = emptyReceive

  def emptyReceive: Behavior[Command] = {
    Behaviors.logMessages(Behaviors.withStash(100) { buffer =>
      Behaviors.receive { (context, message) =>
        message match {
          case enqueue@Enqueue(item) =>
            val nextBehavior = nonEmptyReceive(List(item))
            buffer.unstashAll(nextBehavior)
          case dequeue: Dequeue =>
            buffer.stash(dequeue)
            Behaviors.same
        }
      }
    })
  }

  def nonEmptyReceive(items: List[Int]): Behavior[Command] = {
    Behaviors.logMessages(Behaviors.receive { (context, message) =>
      message match {
        case Enqueue(item) => nonEmptyReceive(items :+ item)
        case Dequeue(replyTo) =>
          replyTo ! ConsumerActor.GetResult(items.head)
          determineReceive(items)
      }
    })
  }

  def determineReceive(items: List[Int]): Behavior[Command] = {
    if (items.tail.isEmpty) emptyReceive else nonEmptyReceive(items.tail)
  }

  sealed trait Command
  final case class Enqueue(item: Int) extends Command
  final case class Dequeue(replyTo: ActorRef[ConsumerActor.Command]) extends Command
}

object MainApp {

  def main(args: Array[String]): Unit = {
    val guardian = Behaviors.setup[Nothing] { context =>
      val queue = context.spawn(ActorQueue(), "actor-queue")

      val pairs =
        for (i <- 1 to 5) yield {
          val producer = context.spawn(ProducerActor(queue), s"producer-actor-$i")
          val consumer = context.spawn(ConsumerActor(queue), s"consumer-actor-$i")
          (consumer, producer)
        }

      pairs.foreach {
        case (consumer, producer) =>
          consumer ! ConsumerActor.Start
          producer ! ProducerActor.Start
      }
      Behaviors.empty
    }
    ActorSystem[Nothing](guardian, "example")
  }
}