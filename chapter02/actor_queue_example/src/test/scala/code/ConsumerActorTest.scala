package code

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import code.ActorQueue.{Dequeue, Enqueue}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

/**
 * @author will.109
 * @date 2020/02/21
 **/
class ConsumerActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "ConsumerActor" must {
    "send enqueue to the queue upon receiving 'start'" in {
      val queueActorTestProb = createTestProbe[ActorQueue.Command]()
      val consumerActor = spawn(ConsumerActor(queueActorTestProb.ref))

      consumerActor ! ConsumerActor.Start
      queueActorTestProb.expectMessage(Dequeue(consumerActor))
      queueActorTestProb.expectNoMessage(100.millis)
    }

    "send a dequeue message to the queue, upon receiving 1000 integers" in {
      val queueActorTestProb = createTestProbe[ActorQueue.Command]()
      val producerActor = spawn(ProducerActor(queueActorTestProb.ref))
      val consumerActor = spawn(ConsumerActor(queueActorTestProb.ref))

      producerActor ! ProducerActor.Start
      (1 to 1000).foreach(i => queueActorTestProb.expectMessage(Enqueue(i)))
      consumerActor ! ConsumerActor.Start
      queueActorTestProb.expectMessage(Dequeue(consumerActor))
    }
  }
}
