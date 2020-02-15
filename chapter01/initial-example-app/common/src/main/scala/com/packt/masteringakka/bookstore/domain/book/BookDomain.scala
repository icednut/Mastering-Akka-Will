package com.packt.masteringakka.bookstore.domain.book

import java.util.Date

import akka.actor.typed.ActorRef
import com.packt.masteringakka.bookstore.common.ServiceResult

//Persistent entities
case class Book(id:Int, title:String, author:String, tags:List[String], cost:Double, inventoryAmount:Int, createTs:Date, modifyTs:Date, deleted:Boolean = false)

//Lookup operations
trait BookEvent
case class FindBook(id:Int, replyTo: ActorRef[ServiceResult[Int]]) extends BookEvent
case class FindBooksForIds(ids:Seq[Int]) extends BookEvent
case class FindBooksByTags(tags:Seq[String]) extends BookEvent
case class FindBooksByTitle(title:String) extends BookEvent
case class FindBooksByAuthor(author:String) extends BookEvent

//Modify operations
case class CreateBook(title:String, author:String, tags:List[String], cost:Double)
case class AddTagToBook(bookId:Int, tag:String)
case class RemoveTagFromBook(bookId:Int, tag:String)
case class AddInventoryToBook(bookId:Int, amount:Int)
case class DeleteBook(id:Int)