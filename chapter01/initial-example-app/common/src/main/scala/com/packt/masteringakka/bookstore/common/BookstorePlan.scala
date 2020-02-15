package com.packt.masteringakka.bookstore.common

import akka.util.Timeout
import com.packt.masteringakka.bookstore.domain.order.SalesOrderStatus
import com.typesafe.scalalogging.LazyLogging
import io.netty.handler.codec.http.HttpResponse
import org.json4s._
import org.json4s.ext.EnumNameSerializer
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import unfiltered.netty.{ServerErrorResponse, async}
import unfiltered.response.{BadRequest, InternalServerError, NotFound, Ok, _}

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author will.109
 * @date 2020/02/14
 **/
trait BookstorePlan extends async.Plan with ServerErrorResponse with LazyLogging {

  import scala.concurrent.duration._

  implicit val ec: ExecutionContext
  implicit val endpointTimeout = Timeout(10.seconds)
  implicit val formats = Serialization.formats(NoTypeHints) + new EnumNameSerializer(SalesOrderStatus)

  def respond(f: Future[Any], resp: unfiltered.Async.Responder[HttpResponse]) = {

    f.onComplete {

      //Got a good result that we can respond with as json
      case util.Success(FullResult(b: AnyRef)) =>
        resp.respond(asJson(ApiResponse(ApiResonseMeta(Ok.code), Some(b))))

      //Got an EmptyResult which will become a 404 with json indicating the not found
      case util.Success(EmptyResult) =>
        resp.respond(asJson(ApiResponse(ApiResonseMeta(NotFound.code, Some(ErrorMessage("notfound")))), NotFound))

      //Got a Failure.  Will either be a 400 for a validation fail or a 500 for everything else
      case util.Success(fail: Failure) =>
        val status = fail.failType match {
          case FailureType.Validation => BadRequest
          case _ => InternalServerError
        }
        val apiResp = ApiResponse(ApiResonseMeta(status.code, Some(fail.message)))
        resp.respond(asJson(apiResp, status))

      //Got a Success for a result type that is not a ServiceResult.  Respond with an unexpected exception
      case util.Success(x) =>
        val apiResp = ApiResponse(ApiResonseMeta(InternalServerError.code, Some(ServiceResult.UnexpectedFailure)))
        resp.respond(asJson(apiResp, InternalServerError))

      //The Future failed, so respond with a 500
      case util.Failure(ex) =>
        logger.error(ex.getMessage, ex)
        val apiResp = ApiResponse(ApiResonseMeta(InternalServerError.code, Some(ErrorMessage("500", Some(ex.getMessage)))))
        resp.respond(asJson(apiResp, InternalServerError))
    }
  }

  def asJson[T <: AnyRef](apiResp: ApiResponse[T], status: Status = Ok) = {
    val ser = write(apiResp)
    status ~> JsonContent ~> ResponseString(ser)
  }

  /**
   * Parses the supplied json String into a type specified by T
   *
   * @param json The json to parse into type T
   * @return an instance of T
   */
  def parseJson[T <: AnyRef : Manifest](json: String) = read[T](json)

  /**
   * Extractor for matching on a path element that is an Int
   */
  object IntPathElement {

    /**
     * Unapply to see if the path element is an Int
     *
     * @param str The string path element to check
     * @return an Option that will be None if not an Int and a Some if an Int
     */
    def unapply(str: String) = util.Try(str.toInt).toOption
  }

}
