package rearview.controller

import play.api.Logger
import play.api.Play.{configuration, current}
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.libs.openid.OpenID
import play.api.mvc.Results.Redirect
import play.api.mvc.Security.{username => defaultUsername}
import play.api.mvc.{Action, AnyContent, BodyParser, Request, Result, WrappedRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Security {

  def username[T](implicit request: Request[T]) = request.session.get(defaultUsername)

  def onUnauthorized[T](implicit request: Request[T]) = Redirect(routes.MainController.unauthorized)

  case class AuthenticatedRequest[T](user: String, request: Request[T]) extends WrappedRequest(request)

  def Authenticated[T](p: BodyParser[T])(f: AuthenticatedRequest[T] => Future[Result]) = {
    Action.async(p) { implicit request =>
      username.map { user =>
        f(AuthenticatedRequest(user, request))
      }.getOrElse(Future.successful(onUnauthorized(request)))
    }
  }

  import play.api.mvc.BodyParsers._
  def Authenticated(f: AuthenticatedRequest[AnyContent] => Future[Result]): Action[AnyContent]  = {
    Authenticated(parse.anyContent)(f)
  }
}

trait OpenIDLogin {
  lazy val authDomain  = configuration.getString("openid.domain").getOrElse(sys.error("openid.domain must be defined in configuration"))
  lazy val openidHost  = configuration.getString("openid.host").getOrElse(sys.error("openid.host must be defined in configuration"))

  def callbackUri()(implicit request: Request[Any]): String
  def successUri()(implicit request: Request[Any]): String

  def login = Action.async { implicit request =>
    val attrs    = "email"     -> "http://axschema.org/contact/email" ::
                   "firstName" -> "http://axschema.org/namePerson/first" ::
                   "lastName"  -> "http://axschema.org/namePerson/last" :: Nil
    OpenID.redirectURL(openidHost, callbackUri, attrs) map { url =>
      Redirect(url)
    } recover {
      case e: Throwable =>
        Redirect(routes.MainController.unauthorized)
    }
  }

  def loginCallback = Action.async { implicit request =>
    Logger.debug("loginCallback: " + request)
    OpenID.verifiedId map { userInfo =>
      userInfo.attributes.get("email") match {
        case Some(email) if(email.endsWith("@" + authDomain)) =>
          userFromOpenID(email, userInfo.attributes("firstName"), userInfo.attributes("lastName")) match {
            case Some(user) => Redirect(successUri).withSession(defaultUsername -> email, MainController.USER -> toJson(user).toString())
            case _          => Redirect(successUri).withSession(defaultUsername -> email)
          }

        case other =>
          Logger.info("Redeemed, but no email: " + other)
          Redirect(routes.MainController.unauthorized)
      }
    } recover {
      case e: Throwable =>
        Logger.debug("Failed login", e)
        Redirect(routes.MainController.unauthorized)
    }
  }


  protected def userFromOpenID(email: String, firstName: String, lastName: String): Option[JsValue]
}
