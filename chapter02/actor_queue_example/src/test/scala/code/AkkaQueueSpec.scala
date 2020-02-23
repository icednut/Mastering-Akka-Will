package code

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import code.ActorQueue.{Dequeue, Enqueue}
import code.ConsumerActor.GetResult
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class AkkaQueueSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "ActorQueue" should {
    "enqueue and dequeue an item" in {
      val replyProbe = createTestProbe[ConsumerActor.Command]()
      val actorQueue = spawn(ActorQueue())

      actorQueue ! Enqueue(1)
      actorQueue ! Dequeue(replyProbe.ref)
      replyProbe.expectMessage(GetResult(1))
    }

    "stash a dequeue message when the actor is empty" in {
      val replyProbe = createTestProbe[ConsumerActor.Command]()
      val actorQueue = spawn(ActorQueue())

      actorQueue ! Dequeue(replyProbe.ref) // stash this message because the actor starts empty
      replyProbe.expectNoMessage(100.millis)
      actorQueue ! Enqueue(1)
      replyProbe.expectMessage(GetResult(1)) // should immediately be dequeued because of the unstashed Dequeue message
      replyProbe.expectNoMessage(100.millis)
    }
  }

}
