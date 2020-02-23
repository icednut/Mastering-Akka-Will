package code

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Terminated}

/**
 * @author will.109
 * @date 2020/02/21
 **/
object ShutdownReaper {

  def apply(): Behavior[Command] = shutdownReceive(0)

  def shutdownReceive(watching: Int): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.receiveMessage[Command] {
        case Monitor(ref) =>
          context.watch(ref)
          shutdownReceive(watching + 1)
      }.receiveSignal {
        case (_, Terminated(_)) if watching - 1 == 0 =>
          context.log.info("All consumers done, terminating actor system")
          context.system.terminate()
          Behaviors.stopped
        case (_, Terminated(_)) if watching - 1 == 0 =>
          shutdownReceive(watching - 1)
      }
    }
  }

  sealed trait Command

  final case class Monitor(ref: ActorRef[_]) extends Command

}
