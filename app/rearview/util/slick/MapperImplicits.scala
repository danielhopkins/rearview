package rearview.util.slick

import java.sql.Timestamp
import java.util.Date

import play.api.libs.json.{JsValue, Json, JsString, JsArray}
import rearview.model.JobStatus

import scala.slick.driver.JdbcDriver

object MapperImplicits extends JdbcDriver {

  import rearview.Global.slickDriver.simple._

  implicit val dateColumnType           = MappedColumnType.base[Date, Timestamp](d => new Timestamp(d.getTime), ts => new Date(ts.getTime))
  implicit val arrayOfStringsColumnType = MappedColumnType.base[List[String], String](
    v => Json.stringify(JsArray(v.map(JsString))),
    s => Json.parse(s) match {
      case JsArray(l) => l.map(_.asInstanceOf[JsString].value).toList
      case _          => Nil
    })
  implicit val JsValueColumnType = MappedColumnType.base[JsValue, String](Json.stringify, Json.parse)

  implicit val jobStatusColumnType = MappedColumnType.base[JobStatus,String](
    _.name,
    s => JobStatus.unapply(s).getOrElse(sys.error(s"Missing mapping for JobStatus: $s"))
  )

}
