package rearview.monitor

import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.specification.AroundExample
import play.api.libs.json._
import play.api.test.FakeApplication
import play.api.test.Helpers.running
import rearview.graphite._
import rearview.util.FutureMatchers

import scala.io.Source

class LoadSpec extends Specification with AroundExample with FutureMatchers {

  sequential

  lazy val artifact  = GraphiteParser(Source.fromFile("test/monitor.dat").getLines.reduceLeft(_ + "\n" + _))

  def application = FakeApplication(additionalConfiguration = Map(
      "db.default.url" -> "jdbc:mysql://localhost:3306/rearview_test",
      "ehcacheplugin" -> "disabled",
      "logger.application" -> "OFF"))

  def around[R : AsResult](r: => R) = {
    running(application) {
      AsResult(r)
    }
  }

  "Analytics" should {
    "load test" in {
      skipped("disabled")
      val monitorExpr   = """
        total = fold_metrics(0) do |accum, a|
          accum + a.value.to_f
        end
        raise "Total should be greater than 0" if !(total > 0)
      """

      (1 to 1000).par.foreach { i =>
         Monitor.eval(artifact, Some(monitorExpr), JsObject(Nil), true).status
      }
      true
    }
  }
}
