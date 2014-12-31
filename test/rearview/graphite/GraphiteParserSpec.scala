package rearview.graphite

import org.specs2.mutable.Specification

import scala.io.Source

class GraphiteParserSpec extends Specification {

  lazy val artifact   = Source.fromFile("test/test.dat").getLines.reduceLeft(_ + "\n" + _)
  lazy val nanPayload = Source.fromFile("test/nan.dat").getLines.reduceLeft(_ + "\n" + _)

  "Parser" should {
    "handle graphite data" in {
      val data = GraphiteParser(artifact)
      data.length === 3
    }

    "handle NaN in graphite data" in {
      val data = GraphiteParser(nanPayload)
      data.length === 3
    }

    "handle no data" in {
      val data = GraphiteParser("")
      data.length === 0
    }
  }
}
