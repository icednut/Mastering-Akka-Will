package code

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.util.Timeout
import code.WorkMaster.{DoWorkerWork, IterationCount}

import scala.concurrent.duration._


/**
 * @author will.109
 * @date 2020/02/24
 **/
object ParallelismWorker {

  def apply(): Behavior[WorkMaster.Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case DoWorkerWork(workMaster) =>
          var totalIterations = 0L
          var count = 10000000
          while (count > 0) {
            totalIterations += 1
            count -= 1
          }
          workMaster ! IterationCount(totalIterations)
          Behaviors.same
      }
    }
  }
}

object WorkMaster {

  def apply(workerCount: Int): Behavior[Command] = {
    Behaviors.setup { context =>
      val pool = Routers.pool(workerCount)(
        Behaviors.supervise(ParallelismWorker()).onFailure(SupervisorStrategy.restart)
      )
      val workers: ActorRef[Command] = context.spawn(pool, "worker-pool")
      waitingForRequest(workers, context)
    }
  }

  private def waitingForRequest(workers: ActorRef[Command], context: ActorContext[Command]): Behavior[Command] = {
    Behaviors.receiveMessage {
      case StartProcessing(replyTo) =>
        val requestCount = 50000
        for (i <- 1 to requestCount) {
          workers ! DoWorkerWork(context.self)
        }
        collectingResults(replyTo, requestCount)
    }
  }

  private def collectingResults(replyTo: ActorRef[Command],
                                remaining: Int,
                                iterations: Long = 0): Behavior[Command] = {
    Behaviors.receiveMessage {
      case IterationCount(count) =>
        val newRemaining = remaining - 1
        val newIterations = count + iterations
        if (newRemaining == 0) {
          replyTo ! IterationCount(newIterations)
          Behaviors.stopped
        } else {
          collectingResults(replyTo, newRemaining, newIterations)
        }
    }
  }

  sealed trait Command

  case class IterationCount(count: Long) extends Command

  case class DoWorkerWork(workMaster: ActorRef[Command]) extends Command

  case class StartProcessing(replyTo: ActorRef[Command]) extends Command

}

object MainApp {

  def main(args: Array[String]): Unit = {
    val workerCount = args.headOption.getOrElse("8").toInt
    val guardian = Behaviors.setup[Nothing] { context =>
      implicit val timeout: Timeout = 60.seconds
      implicit val ec = context.executionContext
      implicit val system = context.system

      val workMaster = context.spawn(WorkMaster(workerCount), "work-master")
      val start = System.currentTimeMillis()
      workMaster.ask[WorkMaster.Command](WorkMaster.StartProcessing)
        .mapTo[IterationCount]
        .flatMap { iterations =>
          val time = System.currentTimeMillis() - start
          context.log.info(s"total time was: $time ms")
          context.log.info(s"total iterations was: ${iterations.count}")
          system.whenTerminated
        }
        .recover {
          case t: Throwable =>
            context.log.error(t.getMessage, t)
            system.whenTerminated
        }
      Behaviors.empty
    }
    ActorSystem[Nothing](guardian, "word-counter")
  }
}