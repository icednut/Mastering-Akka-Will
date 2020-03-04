package com.packt.masteringakka.bookstore.common

import akka.actor.typed.ActorRef
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * @author will.109
 * @date 2020/02/15
 **/
trait HttpResponseMixin extends LazyLogging {

  private val toFailure: PartialFunction[Throwable, ServiceResult[Nothing]] = {
    case ex => com.packt.masteringakka.bookstore.common.Failure(FailureType.Service, ServiceResult.UnexpectedFailure, Some(ex))
  }

  def pipeResponse[T](f: Future[T], replyTo: ActorRef[ServiceResult[T]])(implicit ex: ExecutionContext): Unit = {
    f.
      map {
        case o: Option[T] => ServiceResult.fromOption(o)
        case f: com.packt.masteringakka.bookstore.common.Failure => f
        case other => FullResult(other)
      }.
      recover(toFailure).
      onComplete {
        case Success(value) =>
          logger.info(value.toString)
          replyTo ! value
        case Failure(fa: Throwable) =>
          logger.error(fa.getMessage, fa)
          replyTo ! com.packt.masteringakka.bookstore.common.Failure(FailureType.Service, ServiceResult.UnexpectedFailure, Some(fa))
        // TODO: Fail 부분이 이상함. recover에서 Failure 인스턴스를 만들어서 반환했는데 왜 그걸 못쓰는걸까?
      }
  }
}
