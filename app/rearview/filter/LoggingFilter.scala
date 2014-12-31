package rearview.filter

import play.api.Logger
import play.api.mvc.{Filter, RequestHeader, Result}
import rearview.Global

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object LoggingFilter extends Filter {

  /**
   * Wait for result the log uri, status, etc
   */
  def apply(next: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis
    val result    = next(rh)

    result map { result =>
      if(Global.accessLogging) {
        val elapsed = System.currentTimeMillis - startTime
        val header  = result.header
        val logLine = s"${rh.remoteAddress} ${rh.method} ${header.status} ${elapsed} ${rh.uri}"
        Logger("access").info(logLine)
      }
    }

    result
  }
}