package com.packt.masteringakka.bookstore.user

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.packt.masteringakka.bookstore.common.{BookstorePlan, ServiceResult}
import com.packt.masteringakka.bookstore.domain.user._
import io.netty.channel.ChannelHandler.Sharable
import unfiltered.request._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

/**
 * Http endpoint class for performing user related actions
 */
@Sharable
class UserEndpoint(userManager: ActorRef[UserEvent], system: ActorSystem[Nothing])(implicit val ec: ExecutionContext) extends BookstorePlan {

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler = system.scheduler

  def intent = {
    /**
     * 사용자 정보 조회
     */
    case req@GET(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val f = userManager.ask((ref: ActorRef[ServiceResult[_]]) => FindUserById(userId, ref))
      respond(f, req)

    /**
     * email로 사용자 정보 조회
     */
    case req@GET(Path(Seg("api" :: "user" :: Nil))) & Params(EmailParam(email)) =>
      val f = userManager.ask((ref: ActorRef[ServiceResult[_]]) => FindUserByEmail(email, ref))
      respond(f, req)

    /**
     * 사용자 정보 등록하기
     */
    case req@POST(Path(Seg("api" :: "user" :: Nil))) =>
      val input = parseJson[UserInput](Body.string(req))
      val f = userManager.ask((ref: ActorRef[ServiceResult[_]]) => CreateUser(input, ref))
      respond(f, req)

    /**
     * 사용자 정보 수정하기
     */
    case req@PUT(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val input = parseJson[UserInput](Body.string(req))
      val f = userManager.ask((ref: ActorRef[ServiceResult[_]]) => UpdateUserInfo(userId, input, ref))
      respond(f, req)

    /**
     * 사용자 정보 삭제하기
     */
    case req@DELETE(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val f = userManager.ask((ref: ActorRef[ServiceResult[_]]) => DeleteUser(userId, ref))
      respond(f, req)
  }

  /** Unfiltered param for email address */
  object EmailParam extends Params.Extract("email", Params.first ~> Params.nonempty)

}