package rearview.controller

import play.api.libs.json._
import play.api.mvc.{BodyParsers, Controller}
import rearview.graphite.{ConfigurableHttpClient, LiveGraphiteClient}
import rearview.model.Job
import rearview.model.ModelImplicits._
import rearview.monitor.Monitor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MonitorController extends Controller  with Security {

  implicit def graphiteClient: ConfigurableHttpClient

  def monitor = Authenticated[JsValue](BodyParsers.parse.tolerantJson) {  implicit request =>
    request.body.validate[Job] match {
      case JsError(e)        => Future.successful(BadRequest(e.toString))
      case JsSuccess(job, _) =>
        import job._

        Monitor(metrics, monitorExpr, minutes, job, true, toDate) map { result =>
          Ok(Json.toJson(result.output))
        } recover {
          case e: Throwable => InternalServerError(e.getMessage)
        }
    }
  }
}

object MonitorController extends MonitorController {
  lazy val graphiteClient = LiveGraphiteClient
}
