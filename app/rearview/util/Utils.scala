package rearview.util

import rearview.Global
import rearview.model.Job

object Utils {
  /**
   * Returns the correct uri for a given servername/port
   */
  def jobUri(job: Job) = {
    s"http://${Global.externalHostname}/#dash/${job.appId}/expand/${job.id.get}"
  }

  /**
   * Exit with given error code and message
   */
  def exitMsg(msg: String, code: Int = -1): Nothing = {
    sys.error(msg)
    sys.exit(code)
  }
}