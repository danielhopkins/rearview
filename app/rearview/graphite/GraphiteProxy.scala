package rearview.graphite

import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws.WS
import play.api.mvc.Results.{EmptyContent, Ok, Unauthorized}
import play.api.mvc.{ResponseHeader, Result, SimpleResult}
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class GraphiteResponse(status: Int, body: Array[Byte], headers: Map[String, String] = Map())


trait ConfigurableHttpClient {
  def get(uri: String, headers: (String, String)*): Future[GraphiteResponse]
}


class MockGraphiteClient(val response: GraphiteResponse) extends ConfigurableHttpClient {
  override def get(uri: String, headers: (String, String)*): Future[GraphiteResponse] = {
    Future.successful(response)
  }
}


object LiveGraphiteClient extends ConfigurableHttpClient {
  def get(uri: String, headers: (String, String)*): Future[GraphiteResponse] = {
    WS.url(uri).withHeaders(headers:_*).get().map { r =>
      val ahcHeaders = r.allHeaders
      val headers = ahcHeaders.keySet.foldLeft(Map[String, String]()) { (m, k) =>
        m + (k -> ahcHeaders(k).headOption.getOrElse(""))
       }
      GraphiteResponse(r.status, r.body.getBytes, headers)
    }
  }
}


/**
 * Makes Graphite API calls on behalf of a downstream client which is authenticated with Rearview.
 */
object GraphiteProxy {

  def apply[T](uri: String, credentials: String)(h: Future[GraphiteResponse] => Future[T])(implicit client: ConfigurableHttpClient) = {
    h(client.get(uri, ("Authorization", "Basic " + credentials)))
  }

  def defaultHandler(response: Future[GraphiteResponse]): Future[Result] = {
    response.map { r =>
      r.status match {
        case Ok.header.status =>
          Result(Ok.header, Enumerator(r.body)).as(r.headers.get("Content-Type").getOrElse("text/plain"))

        case code @ Unauthorized.header.status =>
          val headers = r.headers.get("WWW-Authenticate").map(h => Map("WWW-Authenticate" -> h))
          val responseHeader = new ResponseHeader(code, headers.getOrElse(Map()))
          Result(responseHeader, Enumerator())

        case code =>
          Logger.warn("Received " + code + " from Graphite")
          val responseHeader = new ResponseHeader(code, Map())
          Result(responseHeader, Enumerator())
      }
    }
  }
}
