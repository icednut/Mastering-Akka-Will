package code

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

/**
 * @author will.109
 * @date 2020/02/21
 **/
object ProducerActor {

  def apply(queue: ActorRef[ActorQueue.Command]): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case Start =>
          for (i <- 1 to 5) queue ! ActorQueue.Enqueue(i)
          Behaviors.same
      }
    }
  }

  sealed trait Command
  final case object Start extends Command
}
