package code

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import code.ActorQueue.Enqueue
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

/**
 * @author will.109
 * @date 2020/02/21
 **/
class ProducerActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "ProducerActor" must {
    "produce 1000 enqueue messages upon receiving 'start'" in {
      val queueActorTestProb = createTestProbe[ActorQueue.Command]()
      val producerActor = spawn(ProducerActor(queueActorTestProb.ref))

      producerActor ! ProducerActor.Start
      (1 to 1000).foreach(i => queueActorTestProb.expectMessage(Enqueue(i)))
      queueActorTestProb.expectNoMessage(100.millis)
    }
  }
}
