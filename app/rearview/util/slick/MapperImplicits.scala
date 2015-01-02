package rearview.util.slick

import java.sql.Timestamp
import java.util.Date

import play.api.libs.json.{JsValue, Json, JsString, JsArray}
import rearview.model._
import ModelImplicits._

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

  implicit val jobStatusColumnType = MappedColumnType.base[JobStatus, String](
    _.name,
    s => JobStatus.unapply(s).getOrElse(sys.error(s"Missing mapping for JobStatus: $s"))
  )

  implicit val alertKeyColumn = MappedColumnType.base[List[AlertKey], JsValue](
    ks  => JsArray(ks.map(AlertKeyFormat.writes)),
    {
      case JsArray(ks) => ks.map(AlertKeyFormat.reads(_).getOrElse(sys.error("Unable to read AlertKey"))).toList
      case _           => sys.error("Unable to read AlertKey")
    }
  )
}
