package com.packt.masteringakka.bookstore.book

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.packt.masteringakka.bookstore.common.{BookstoreDao, ManagerActor}
import com.packt.masteringakka.bookstore.domain.book._
import slick.dbio.DBIOAction
import slick.jdbc.GetResult
import slick.jdbc.H2Profile.api._

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author will.109
 * @date 2020/02/14
 **/
object BookManager extends ManagerActor {
  val Name = "book-manager"

  def apply(): Behavior[BookEvent] = {
    Behaviors.receive((context, message) => {
      implicit val ec = context.executionContext
      val dao = new BookManagerDao

      message match {
        case FindBook(id, replyTo) =>
          context.log.info("Looking up book for id: {}", id)
          val result: Future[Option[Book]] = dao.findBookById(id)
          pipeResponse(result, replyTo)
          Behaviors.same
        case FindBooksByTags(tags, ref) =>
          Behaviors.same
        case FindBooksByAuthor(author, ref) =>
          Behaviors.same
        case CreateBookAndReply(createBook, ref) =>
          Behaviors.same
        case AddTagToBook(bookId, tag, ref) =>
          Behaviors.same
        case RemoveTagFromBook(bookId, tag, ref) =>
          Behaviors.same
        case DeleteBook(bookId, ref) =>
          Behaviors.same
      }
    })
  }

}

/**
 * Companion to the BookManagerDao
 */
object BookManagerDao {
  implicit val GetBook = GetResult { r => Book(r.<<, r.<<, r.<<, r.nextString.split(",").filter(_.nonEmpty).toList, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp) }
  val BookLookupPrefix =
    """
    select b.id, b.title, b.author, array_to_string(array_agg(t.tag), ',') as tags, b.cost, b.inventoryAmount, b.createTs, b.modifyTs
    from Book b left join BookTag t on b.id = t.bookId where
  """
}

class BookManagerDao(implicit ec: ExecutionContext) extends BookstoreDao {

  import BookManagerDao._
  import DaoHelpers._

  /**
   * Finds a single book by its id
   *
   * @param id The id of the book to find
   * @return a Future for an Option[Book]
   */
  def findBookById(id: Int) = findBooksByIds(Seq(id)).map(_.headOption)

  /**
   * Finds a Vector of Books by their ids
   *
   * @param ids The ids to get books for
   * @return a Future for a Vector[Book]
   */
  def findBooksByIds(ids: Seq[Int]) = {
    val idsParam = s"${ids.mkString(",")}"
    db.run(sql"""#$BookLookupPrefix b.id in (#$idsParam) and not b.deleted group by b.id""".as[Book])
  }

  /**
   * Finds the ids of books that have all of the supplied tags on them
   *
   * @param tags The tags that the books must have all of
   * @return a Future for a Vector[Int] which is the ids of the matching books
   */
  def findBookIdsByTags(tags: Seq[String]) = {
    val tagsParam = tags.map(t => s"'${t.toLowerCase}'").mkString(",")
    val idsWithAllTags = db.run(sql"select bookId, count(bookId) from BookTag where lower(tag) in (#$tagsParam) group by bookId having count(bookId) = ${tags.size}".as[(Int, Int)])
    idsWithAllTags.map(_.map(_._1))
  }

  /**
   * Finds a Vector of Book for books with a matching author, using fuzzy matching
   *
   * @param author The author to match on
   * @return a Future for a Vector of Books that match
   */
  def findBooksByAuthor(author: String) = {
    val param = s"%${author.toLowerCase}%"
    db.run(sql"""#$BookLookupPrefix lower(b.author) like $param and not b.deleted group by b.id""".as[Book])
  }

  /**
   * Creates a new Book in the system
   *
   * @param book The book to create
   * @return a Future for a Book with the new id assigned
   */
  def createBook(book: Book) = {
    val insert =
      sqlu"""
        insert into Book (title, author, cost, inventoryamount, createts)
        values (${book.title}, ${book.author}, ${book.cost}, ${book.inventoryAmount}, ${book.createTs.toSqlDate})
      """
    val idget = lastIdSelect("book")

    def tagsInserts(bookId: Int) = DBIOAction.sequence(book.tags.map(t => sqlu"insert into BookTag (bookid, tag) values ($bookId, $t)"))

    val txn =
      for {
        bookRes <- insert
        id <- idget
        if id.headOption.isDefined
        _ <- tagsInserts(id.head)
      } yield {
        book.copy(id = id.head)
      }

    db.run(txn.transactionally)
  }

  /**
   * Adds a new tag to a Book
   *
   * @param book The book to tag
   * @param tag  The tag to add
   * @return a Future for the Book with the tag on it
   */
  def tagBook(book: Book, tag: String) = {
    db.run(sqlu"insert into BookTag values (${book.id}, $tag)").map(_ => book.copy(tags = book.tags :+ tag))
  }

  /**
   * Removed a tag from a Book
   *
   * @param book The book to remove the tag from
   * @param tag  The tag to remove
   * @return a Future for the Book with the tag removed
   */
  def untagBook(book: Book, tag: String) = {
    db.run(sqlu"delete from BookTag where bookId =  ${book.id} and tag = $tag").
      map(_ => book.copy(tags = book.tags.filterNot(_ == tag)))
  }

  /**
   * Adds inventory to the book so it can start being sold
   *
   * @param book   The book to add inventory to
   * @param amount The amount to add
   * @return a Future for a Book with the new inventory amount on it
   */
  def addInventoryToBook(book: Book, amount: Int) = {
    db.run(sqlu"update Book set inventoryAmount = inventoryAmount + $amount where id = ${book.id}").
      map(_ => book.copy(inventoryAmount = book.inventoryAmount + amount)) //Not entirely accurate in that others updates could have happened
  }

  /**
   * Soft deletes a book from the system
   *
   * @param book the book to delete
   * @return a Future for the Book that was deleted
   */
  def deleteBook(book: Book) = {
    val bookDelete = sqlu"update Book set deleted = true where id = ${book.id}"
    db.run(bookDelete).map(_ => book.copy(deleted = true))
  }
}