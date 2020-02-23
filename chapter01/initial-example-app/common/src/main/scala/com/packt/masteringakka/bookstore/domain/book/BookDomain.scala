package com.packt.masteringakka.bookstore.domain.book

import java.util.Date

import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
import com.packt.masteringakka.bookstore.common.ServiceResult

//Persistent entities
case class Book(id:Int, title:String, author:String, tags:List[String], cost:Double, inventoryAmount:Int, createTs:Date, modifyTs:Date, deleted:Boolean = false)

//Lookup operations
trait BookEvent
case class FindBook(id:Int, replyTo: ActorRef[ServiceResult[_]]) extends BookEvent
case class FindBooksForIds(ids:Seq[Int]) extends BookEvent
case class FindBooksByTags(tags:Seq[String], replyTo: ActorRef[ServiceResult[_]]) extends BookEvent
case class FindBooksByTitle(title:String) extends BookEvent
case class FindBooksByAuthor(author:String, replyTo: ActorRef[ServiceResult[_]]) extends BookEvent

//Modify operations
case class CreateBook(title:String, author:String, tags:List[String], cost:Double)
case class CreateBookAndReply(createBook: CreateBook, replyTo: ActorRef[ServiceResult[_]]) extends BookEvent
case class AddTagToBook(bookId:Int, tag:String, replyTo: ActorRef[ServiceResult[_]]) extends BookEvent
case class RemoveTagFromBook(bookId:Int, tag:String, replyTo: ActorRef[ServiceResult[_]]) extends BookEvent
case class AddInventoryToBook(bookId:Int, amount:Int, replyTo: ActorRef[ServiceResult[_]]) extends BookEvent
case class DeleteBook(id:Int, replyTo: ActorRef[ServiceResult[_]]) extends BookEvent

object BookDomain {
  val Name = "book-manager"
  val BookManagerKey = ServiceKey[BookEvent](Name)
}