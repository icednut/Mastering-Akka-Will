package code

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

/**
 * @author will.109
 * @date 2020/02/21
 **/
object ConsumerActor {

  def apply(queue: ActorRef[ActorQueue.Command]): Behavior[Command] =
    consumerReceive(queue, 5)

  def consumerReceive(queue: ActorRef[ActorQueue.Command], remaining: Int): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case Start =>
          queue ! ActorQueue.Dequeue(context.self)
          Behaviors.same
        case GetResult(item) =>
          var newRemaining = remaining - 1
          context.log.info(s"Consumer ${context.self.path} receive == $item")
          if (newRemaining == 0) {
            context.log.info(s"Consumer ${context.self.path} is done consuming")
            Behaviors.stopped
          } else {
            queue ! ActorQueue.Dequeue(context.self)
            consumerReceive(queue, newRemaining)
          }
      }
    }
  }

  sealed trait Command
  final case class GetResult(item: Int) extends Command
  final case object Start extends Command
}
