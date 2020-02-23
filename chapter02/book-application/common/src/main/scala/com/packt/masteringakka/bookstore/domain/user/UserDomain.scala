package com.packt.masteringakka.bookstore.domain.user

import java.util.Date

import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
import com.packt.masteringakka.bookstore.common.ServiceResult

//Persistent entities
case class BookstoreUser(id:Int, firstName:String, lastName:String, email:String, createTs:Date, modifyTs:Date, deleted:Boolean = false)

//Lookup Operations
sealed trait UserEvent
case class FindUserById(id: Int, actorRef: ActorRef[ServiceResult[_]]) extends UserEvent
case class FindUserByEmail(email:String, actorRef: ActorRef[ServiceResult[_]]) extends UserEvent

//Modify operations
case class UserInput(firstName:String, lastName:String, email:String)
case class CreateUser(input:UserInput, actorRef: ActorRef[ServiceResult[_]]) extends UserEvent
case class UpdateUserInfo(id:Int, input:UserInput, actorRef: ActorRef[ServiceResult[_]]) extends UserEvent
case class DeleteUser(userId:Int, actorRef: ActorRef[ServiceResult[_]]) extends UserEvent

object UserDomain {
  val Name = "user-manager"
  val UserManagerKey = ServiceKey[UserEvent](Name)
}
