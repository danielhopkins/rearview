package rearview.dao

import java.util.Date
import play.api.libs.json._
import rearview.Global._
import rearview.model._
import rearview.model.JobStatus
import org.joda.time._
import slickDriver.simple._

/**
 * Data access layer for Job objects.
 */
object JobDAO {

  import rearview.util.slick.MapperImplicits._

  val constTrue = new LiteralColumn[Boolean](true)

  /**
   * Column to attribute mappings for the Job class
   */
  class Jobs(tag: Tag) extends Table[Job](tag, "jobs") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId        = column[Long]("user_id")
    def appId         = column[Long]("app_id")
    def name          = column[String]("name")
    def cronExpr      = column[String]("cron_expr")
    def metrics       = column[List[String]]("metrics")
    def monitorExpr   = column[Option[String]]("monitor_expr")
    def minutes       = column[Option[Int]]("minutes")
    def toDate        = column[Option[String]]("to_date")
    def description   = column[Option[String]]("description")
    def active        = column[Boolean]("active")
    def status        = column[Option[JobStatus]]("status")
    def lastRun       = column[Option[Date]]("last_run")
    def nextRun       = column[Option[Date]]("next_run")
    def alertKeys     = column[Option[List[AlertKey]]]("alert_keys")
    def errorTimeout  = column[Int]("error_timeout")
    def createdAt     = column[Option[Date]]("created")
    def modifiedAt    = column[Option[Date]]("modified")
    def deletedAt     = column[Option[Date]]("deleted_at")
    def *             = (id.?, userId, appId, name, cronExpr, metrics, monitorExpr, minutes, toDate, description, active, status, lastRun, nextRun, alertKeys, errorTimeout, createdAt, modifiedAt, deletedAt) <> (Job.tupled, Job.unapply)
  }


  /**
   * Column to attribute mappings for the JobData class
   */
  class JobData(tag: Tag) extends Table[(Long, Date, String)](tag, "job_data") {
    def jobId     = column[Long]("job_id")
    def createdAt = column[Date]("created")
    def data      = column[String]("data")
    def *         = (jobId, createdAt, data)
  }


  /**
   * Column to attibute mappings for the JobErrors class
   */
  class JobErrors(tag: Tag) extends Table[(Long, Long, Date, JobStatus, Option[String])](tag, "job_errors") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def jobId     = column[Long]("job_id")
    def createdAt = column[Date]("created")
    def message   = column[Option[String]]("message")
    def status    = column[JobStatus]("status")
    def *         = (id, jobId, createdAt, status, message)
  }

  /**
   * Column[Option[Date]] implicit to be able to use conditionals on options.
   */
  implicit class DateOptColumn(val column: Column[Option[Date]]) {
    def ===(stringOpt: Option[Date]): Column[Option[Boolean]] = stringOpt match {
      case Some(n) => column === stringOpt
      case       _ => column.isEmpty
    }
  }


  /**
   * Upserts a job. If an id is defined the call uses an update, otherwise
   * an insert is performed.
   * @param job
   * @return
   */
  def store(job: Job): Option[Job] = database withSession { implicit session: Session =>
    val jobs = TableQuery[Jobs]
    job.id match {
      case Some(id) =>
        jobs filter(_.id === id) update(job)
        job.id
      case None =>
        Some(jobs returning jobs.map(_.id) += job)
    }
  } flatMap { id =>
    findById(id)
  }

  /**
   * Each time a job runs it's data for the last run is saved, replacing the existing record.  We do not currently
   * keep a log, simply the last data.
   * @param jobId
   * @param data
   * @return
   */
  def storeData(jobId: Long, data: JsValue): JsValue = database withSession { implicit session: Session =>
    val jobData = TableQuery[JobData]
    (jobData filter (data => data.jobId === jobId) firstOption) map { r =>
      jobData filter (data => data.jobId === jobId) update (jobId, new Date, data.toString)
    } orElse {
      jobData += (jobId, new Date, data.toString)
      Some(data)
    }
    data
  }


  /**
   * If a job has an error we store the error and it's data.  Unlike job_data we do store a running log of all
   * errors for a job/version.
   * @param jobId
   * @param status
   * @param message
   * @param date
   * @return
   */
  def storeError(jobId: Long, status: JobStatus, message: Option[String] = None, date: Date = new Date): Int = database withSession { implicit session: Session =>
    val jobErrors = TableQuery[JobErrors]
    jobErrors.map(e => (e.jobId, e.createdAt, e.status, e.message)) += (jobId, date, status, message)
  }


  /**
   * Update the status column for a job.
   * @param job
   * @return
   */
  def updateStatus(job: Job): Job = database withSession { implicit session: Session =>
    TableQuery[Jobs] filter(j => j.id === job.id) map(j => (j.status, j.lastRun))  update (job.status, job.lastRun)
    job
  }


  /**
   * Delete a job by id.  Delete means setting the deleted_at timestamp.
   * @param id
   * @return
   */
  def delete(id: Long): Boolean = database withSession { implicit session: Session =>
    (TableQuery[Jobs] filter(_.id === id) map(_.deletedAt) update(Some(new Date))) > 0
  }


  /**
   * Delete a job by application id.  Delete means setting the deleted_at timestamp.
   * @param appId
   * @return
   */
  def deleteByApplication(appId: Long): Boolean = database withSession { implicit session: Session =>
    (TableQuery[Jobs] filter(_.appId === appId) map(_.deletedAt) update(Some(new Date))) > 0
  }



  /**
   * List jobs with a optional active and deleted filters.
   * @param onlyActive
   * @param includeDeleted
   * @return
   */
  def list(onlyActive: Boolean = false, includeDeleted: Boolean = false): List[Job] = database withSession { implicit session: Session =>
    TableQuery[Jobs] filter(j => (if(!includeDeleted) j.deletedAt.isEmpty else constTrue) && (if(onlyActive) j.active === true else constTrue)) list
  }


  /**
   * Find a job by id and optional version.  If no version is specified the latest is returned.
   * @param id
   * @return
   */
  def findById(id: Long): Option[Job] = database withSession { implicit session: Session =>
    TableQuery[Jobs] filter (j => j.id === id) firstOption
  }


  /**
   * Given an application id, return the associated jobs. The active flag may optionally be passed.
   * @param appId
   * @param onlyActive
   * @return
   */
  def findByApplication(appId: Long, onlyActive: Boolean = false): List[Job] = database withSession { implicit session: Session =>
    TableQuery[Jobs] filter(j => j.appId === appId && j.deletedAt.isEmpty && (if(onlyActive) j.active === true else constTrue)) list
  }


  /**
   * Return the data for a given job and optional id (else the most recent version is used).
   * @param jobId
   * @return
   */
  def findData(jobId: Long): Option[JsValue] = database withSession { implicit session: Session =>
    (TableQuery[JobData] filter(data => data.jobId === jobId) firstOption) map { t =>
      Json.parse(t._3)
    }
  }

  /**
   * Return all errors by jobId
   * @param jobId
   * @return
   */
  def findErrorsByJobId(jobId: Long, limit: Int = 50): Seq[JobError] = database withSession { implicit session: Session =>
    foldErrorDurations(((for {
      j <- TableQuery[Jobs] if j.id === jobId
      e <- TableQuery[JobErrors] if j.deletedAt === None && e.jobId === j.id
    } yield (e)) sortBy (_.createdAt.desc) take(limit) list) map { r =>
      JobError(r._1, r._2, r._3, r._4, r._5)
    })
  }


  /**
   * Return all errors by application id
   * @param appId
   * @param limit
   * @return
   */
  def findErrorsByApplicationId(appId: Long, limit: Int = 250): Seq[JobError] = database withSession { implicit session: Session =>
    foldErrorDurations(((for {
      j <- TableQuery[Jobs] if j.appId === appId
      e <- TableQuery[JobErrors] if j.deletedAt === None && e.jobId === j.id
    } yield (e)) sortBy (_.createdAt.desc) take(limit) list) map { r =>
      JobError(r._1, r._2, r._3, r._4, r._5)
    })
  }


  /**
   * Takes a list of errors, groups them into lists by jobId then transforms each list calculating the
   * endDate for each error based on the time of the next success (or max timeout).
   */
  private def foldErrorDurations(errors: Seq[JobError]): Seq[JobError] = {
    errors.reverse.groupBy(_.jobId).map { kv =>
      val tmp = kv._2.foldLeft(List[JobError]()) { (acc, cur) =>
        acc.lastOption match {
          case Some(e) if(cur.status == SuccessStatus) =>
            acc.dropRight(1) :+ e.copy(endDate = Some(cur.date)) :+ cur

          case Some(e) if(e.status == SuccessStatus) =>
            acc :+ cur

          case Some(e) =>
            acc

          case None =>
            acc :+ cur
        }
      }

      // Check the last error. If it's failed status with no endDate, make it now or ERROR_TIMEOUT
      val errors = (tmp.dropRight(1) ++ tmp.lastOption.map { error =>
        List(error.copy(endDate = error.endDate.map(d => d).orElse(Some(new DateTime().toDate))))
      }.getOrElse(Nil)).filterNot(_.status == SuccessStatus)

      (kv._1, errors.reverse)

    }.values.flatten.toList
  }
}
