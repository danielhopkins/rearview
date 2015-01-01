package rearview.dao

import java.util.Date

import play.api.Logger
import rearview.Global._
import rearview.model._
import slickDriver.simple._

/**
 * Database access layer for the User class.
 */
object UserDAO {

  import rearview.util.slick.MapperImplicits._

  /**
   * Slick attribute to column mapping.
   */
  class Users(tag: Tag) extends Table[User](tag, "users") {
    def id         = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email      = column[String]("email")
    def firstName  = column[String]("first_name")
    def lastName   = column[String]("last_name")
    def lastLogin  = column[Option[Date]]("last_login")
    def createdAt  = column[Option[Date]]("created")
    def modifiedAt = column[Option[Date]]("modified")
    def *          = (id.?, email, firstName, lastName, lastLogin, createdAt, modifiedAt) <> (User.tupled, User.unapply)
  }


  /**
   * Upserts a user.  If the id is defined an update is performed, otherwise it's an insert.
   * @param user
   * @return
   */
  def store(user: User): Option[User] = {
    try {
      database withSession { implicit session: Session =>
        val users = TableQuery[Users]
        user.id match {
          case Some(id) =>
            users filter(_.id === id) update(user)
            Some(user)
          case None =>
            Some(user.copy(id = Some(users returning users.map(_.id) += (user))))
        }
      }
    } catch {
      case e: Throwable =>
        Logger.error("Failed to store user", e)
        None
    }
  }


  /**
   * Returns a list of users
   * @return
   */
  def list(): List[User] = database withSession { implicit session =>
    TableQuery[Users] list
  }


  /**
   * Find a user by the given user id.
   * @param id
   * @return
   */
  def findById(id: Long): Option[User] = database withSession { implicit session =>
    TableQuery[Users] filter (_.id === id) firstOption
  }


  /**
   * Find a user by the given email address (email address is unique for all users)
   * @param email
   * @return
   */
  def findByEmail(email: String): Option[User] = database withSession { implicit session =>
    TableQuery[Users] filter (_.email === email) firstOption
  }
}
