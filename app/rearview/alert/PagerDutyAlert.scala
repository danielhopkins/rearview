package rearview.alert

import play.api.Logger
import play.api.libs.json.JsNull
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.Play.current
import rearview.Global
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import rearview.model.{AlertKey, PagerDutyAlertKey, AnalysisResult, Job}
import rearview.model.ModelImplicits._
import rearview.util.Utils

trait PagerDutyAlert extends Alert {
  def client: VictorOpsHttpClient

  /**
   * Implement logic to filter for pager duty keys and send over http client
   */
  def send(job: Job, result: AnalysisResult) {
    job.id map { jobId =>
      job.alertKeys map { ks =>
        val keys = ks.filter {
          case k: PagerDutyAlertKey => true
          case _                    => false
        }

        val (description, payload) = pagerDutyPayload(job, result)
        post(jobId.toString, keys, description, Some(payload))
      }
    }
  }


  /**
   * Creates the pager duty json payload.
   * @param job
   * @param result
   * @return
   */
  def pagerDutyPayload(job: Job, result: AnalysisResult): (String, JsValue) = {
    val jobId              = job.id.getOrElse(sys.error("job.id is not defined!"))
    val defaultDescription = s"Rearview #${job.id.get} ${Utils.jobUri(job)}"

    // Pager duty requires description to be <= 1024
    val description = result.message.map { msg =>
      if(msg.isEmpty()) defaultDescription else s"$msg ${Utils.jobUri(job)}"
    }.getOrElse(defaultDescription).take(1024)

    // Add graphite data to raw
    val payload = Json.toJson(result.output) match {
      case JsObject(fields) => JsObject(fields.filterNot(_._1 == "graph_data") :+ ("data" -> TimeSeriesFormat.writes(result.data)))
      case j => j
    }
    (description, payload)
  }


  /**
   * Create the payload and send to PD over http
   */
  def post(id: String, pagerDutyKeys: List[AlertKey], description: String, results: Option[JsValue]) {
    pagerDutyKeys.foreach { key =>
      val payload = JsObject(
        ("service_key", JsString(key.value)) ::
        ("event_type", JsString("trigger")) ::
        ("incident_key", JsString("rearview/" + id)) ::
        ("description", JsString(description)) ::
        ("details", results.getOrElse(JsNull)) ::
        Nil)

      val msg = JsObject(payload.fields.filterNot(_._1 == "details"))
      Logger.info("Posting PagerDuty trigger: " + msg)
      Global.pagerDutyUri.map( client.post(_, payload))
    }
  }
}


trait PagerDutyHttpClient {
  def post(uri: String, payload: JsValue): Future[Boolean]
}


class LivePagerDutyAlert extends PagerDutyAlert {
  Logger.info("PagerDuty alerts are enabled")

  val client = new VictorOpsHttpClient {
    def post(uri: String, payload: JsValue): Future[Boolean] = {
      Logger.debug(s"Posting PagerDuty to $uri")
      WS.url(uri).post(payload) map { r =>
        Logger.debug(s"Received ${r.status}")
        val status = (r.json \ "status").as[String]
        r.status == 200 && status == "success"
      } recover {
        case e: Throwable =>
          Logger.error("Error posting to PagerDuty", e)
          false
      }
    }
  }
}

