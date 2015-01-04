package rearview.alert

import play.api.Logger
import play.api.Play.current
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.WS
import rearview.Global
import rearview.model.ModelImplicits._
import rearview.model.{AlertKey, AnalysisResult, Job, VictorOpsAlertKey}
import rearview.util.Utils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait VictorOpsAlert extends Alert {
  def client: VictorOpsHttpClient

  def send(job: Job, result: AnalysisResult) {
    job.id map { jobId =>
      job.alertKeys map { ks =>
        val keys = ks.filter {
          case k: VictorOpsAlertKey => true
          case _                    => false
        }

        val (description, payload) = buildPayload(job, result)
        post(job, keys, description, Some(payload))
      }
    }
  }


  def buildPayload(job: Job, result: AnalysisResult): (String, JsValue) = {
    val jobId              = job.id.getOrElse(sys.error("job.id is not defined!"))
    val defaultDescription = s"Rearview #${job.id.get} ${Utils.jobUri(job)}"

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


  def post(job: Job, alertKeys: List[AlertKey], description: String, results: Option[JsValue]) {
    alertKeys.foreach { key =>
      val payload = Json.obj(
        "message_type"        -> "critical",
        "VO_ORGANIZATION_KEY" -> key.value,
        "state_message"       -> description,
        "entity_id"           -> s"rearview/${job.name}"
      )
      val msg = JsObject(payload.fields.filterNot(_._1 == "details"))
      Logger.info("Posting VictorOps: " + msg)
      Global.victoropsUri.map(client.post(_, payload))
    }
  }
}


trait VictorOpsHttpClient {
  def post(uri: String, payload: JsValue): Future[Boolean]
}


class LiveVictorOpsAlert extends VictorOpsAlert {
  Logger.info("VictorOps alerts are enabled")

  val client = new VictorOpsHttpClient {
    def post(uri: String, payload: JsValue): Future[Boolean] = {
      Logger.debug(s"Posting VictorOps to $uri")
      WS.url(uri).post(payload) map { r =>
        Logger.debug(s"Received ${r.status}")
        r.status == 200
      } recover {
        case e: Throwable =>
          Logger.error("Error posting to VictorOps", e)
          false
      }
    }
  }
}

