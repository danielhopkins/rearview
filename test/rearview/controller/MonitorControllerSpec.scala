package rearview.controller

import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Specification
import org.specs2.specification.AroundOutside
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeHeaders, FakeRequest}
import rearview.Global.database
import rearview.Global.slickDriver.simple._
import rearview.dao.{ApplicationDAO, UserDAO}
import rearview.graphite.{GraphiteResponse, MockGraphiteClient}
import rearview.model.ModelImplicits._
import rearview.model.{Application, User, _}

import scala.io.Source
import scala.slick.jdbc.{StaticQuery => Q}

class MonitorControllerSpec extends Specification {

  sequential

  def application = FakeApplication(additionalConfiguration = Map(
      "db.default.url"     -> "jdbc:mysql://localhost:3306/rearview_test",
      "logger.application" -> "ERROR"))

  def jobContext = new AroundOutside[Application] {
    lazy val ctx = {
      val user = UserDAO.store(User(None, email = "test@victorops.com", firstName = "Jeff", lastName = "Simpson")).get
      ApplicationDAO.store(Application(None, name = "Test", userId = user.id.get)).get
    }

    def around[R : AsResult](r:  => R): Result = {
      running(application) {
        database withSession { implicit session: Session =>
          (Q.u + "delete from users").execute
        }
        AsResult(r)
      }
    }

    def outside = ctx
  }

  lazy val username       = "test@victorops.com"
  lazy val monitorPayload = Source.fromFile("test/monitor.dat").getLines().reduceLeft(_+_)
  lazy val nanPayload     = Source.fromFile("test/nan.dat").getLines().reduceLeft(_+_)


  "Monitor Controller" should {

    "support monitor analysis" in jobContext { app: Application =>
      val monitorExpr = Some("total = fold_metrics(0) { |accum, a| accum + a.value.to_f }; raise 'Outage in metric' if total == 0")
      val job = Job(None, app.userId, app.id.get, "test", "0 * * * * ?", List("stats_counts.deals.logins.successful"), monitorExpr, Some(60), None)
      val controller = new MonitorController {
        val graphiteClient = new MockGraphiteClient(GraphiteResponse(200, monitorPayload.getBytes()))
      }
      val result = controller.monitor(FakeRequest(POST, "/monitor", FakeHeaders(), jobFormat.writes(job)).withSession(("username", username)))
      status(result) === 200
      val json = Json.parse(contentAsString(result))
      (json \ "status").as[String] === SuccessStatus.name
    }

    "support parsing graphite data with NaN" in jobContext { app: Application =>
      val monitorExpr = Some("true")
      val job = Job(None, app.userId, app.id.get, "test", "0 * * * * ?", List("stats_counts.deals.logins.successful"), monitorExpr, Some(60), None)
      val controller = new MonitorController {
        val graphiteClient = new MockGraphiteClient(GraphiteResponse(200, nanPayload.getBytes()))
      }
      val result = controller.monitor(FakeRequest(POST, "/monitor", FakeHeaders(), jobFormat.writes(job)).withSession(("username", username)))
      status(result) === 200
      val json = Json.parse(contentAsString(result))
      (json \ "status").as[String] === SuccessStatus.name
    }

    "handle parsing bad json (missing fields)" in jobContext { app: Application =>
      val job = Json.obj(
        "appId"      -> app.id.get,
        "userId"     -> app.userId,
        "jobType"    -> "monitor",
        "name"       -> "testMonitor",
        "recipients" -> "test@victorops.com",
        "active"     -> true,
        "cronExpr"   -> "0 * * * * ?",
        "params"     -> JsNull,
        "errorTimeout" -> 60
      )
      val controller = new MonitorController {
        val graphiteClient = new MockGraphiteClient(GraphiteResponse(200, nanPayload.getBytes()))
      }
      val result = controller.monitor(FakeRequest(POST, "/monitor", FakeHeaders(), job).withSession(("username", username)))
      status(result) === 400
    }

  }
}
