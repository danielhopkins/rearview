package rearview.model

import org.specs2.mutable.Specification
import play.api.libs.json._
import rearview.model.ModelImplicits._

class ModelSpec extends Specification {

   val jobJSON = Json.obj(
        "appId"      -> 1,
        "userId"     -> 1,
        "jobType"    -> "monitor",
        "name"       -> "testMonitor",
        "recipients" -> "test@livingsocial.com",
        "active"     -> true,
        "cronExpr"   -> "0 * * * * ?",
        "metrics"       -> Json.arr("stats_counts.deals.events.test"),
        "minutes"       -> 60,
        "monitorExpr"   -> "total = fold_metrics(0) { |accum, a| accum + a.to_f }; raise 'Outage in metric' if total == 0",
        "errorTimeout" -> 60
      )

  "JSON formats" should {

    "Parse standard job json" in {
       val result = jobFormat.reads(jobJSON).asOpt
       result must beSome
    }

    "Parse pager duty keys" in {
      val keyA = PagerDutyAlertKey("", "a")
      val keyB = PagerDutyAlertKey("", "b")
      val json   = jobJSON + ("alertKeys" -> JsArray(List(AlertKeyFormat.writes(keyA), AlertKeyFormat.writes(keyB))))
      val result = jobFormat.reads(json).asOpt
      result must beSome
      result.get.alertKeys must_== Some(List(keyA, keyB))
    }

  }

}
