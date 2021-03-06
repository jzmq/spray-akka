package com.danielasfregola.quiz.management

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.util.Timeout
import com.danielasfregola.quiz.management.MyQuizProtocol._
import spray.http.StatusCodes
import spray.routing.{HttpService, HttpServiceActor, RequestContext, Route}
import spray.httpx.SprayJsonSupport._

import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by admaster on 16/5/11.
  */
class MyRestInterface extends HttpServiceActor with MyRestApi {
  def receive: Receive = runRoute(routes)
}

trait MyRestApi extends HttpService with ActorLogging {
  actor: Actor =>

  implicit val timeout = Timeout(10 seconds)
  var quizzes = Vector[Quiz]()

  def routes: Route =
    pathPrefix("quizzis") {
      pathEnd {
        post {
          entity(as[Quiz]) {
            quiz => requestContext =>
              val responder = createResponder(requestContext)
              createQuiz(quiz) match {
                case true => responder ! QuizCreated
                case _ => responder ! QuizAlreadyExists
              }
          }
        }
      } ~
        path(Segment) { id =>
          delete { requestContext =>
            val responder = createResponder(requestContext)
            deleteQuiz(id)
            responder ! QuizDeleted
          }
        }
    } ~
      pathPrefix("questions") {
        pathEnd {
          get {
            requestContext =>
              val responder = createResponder(requestContext)
              getRandomQuestion.map(responder ! _).getOrElse(responder ! QuestionNotFound)
          }
        } ~
          path(Segment) { id =>
            get {
              requestContext =>
                val responder = createResponder(requestContext)
                getQuestion(id).map(responder ! _).getOrElse(responder ! QuestionNotFound)
            } ~
              put {
                entity(as[Answer]) {
                  answer =>
                    requestContext =>
                      val responder = createResponder(requestContext)
                      isAnswerCorrect(id, answer) match {
                        case true => responder ! CorrectAnswer
                        case _ => responder ! WrongAnswer
                      }
                }
              }
          }
      } ~
      pathPrefix("helloworld") {
        pathEnd {
          get {
            requestContext =>
              val responder = createResponder(requestContext)
              responder ! "Helloworld O hahaha"
          }
        }
      }

  private def createResponder(requestContext: RequestContext) = {
    context.actorOf(Props(new MyResponder(requestContext)))
  }

  private def createQuiz(quiz: Quiz): Boolean = {
    val doesNotExist = !quizzes.exists(_.id == quiz.id)
    if (doesNotExist) quizzes = quizzes :+ quiz
    doesNotExist
  }

  private def deleteQuiz(id: String): Unit = {
    quizzes = quizzes.filterNot(_.id == id)
  }

  private def getRandomQuestion: Option[Question] = {
    !quizzes.isEmpty match {
      case true =>
        val idx = (new Random).nextInt(quizzes.size)
        Some(quizzes(idx))
      case _ => None
    }
  }

  private def getQuestion(id: String): Option[Question] = {
    getQuiz(id).map(toQuestion)
  }

  private def getQuiz(id: String): Option[Quiz] = {
    quizzes.find(_.id == id)
  }

  private def isAnswerCorrect(id: String, proposedAnswer: Answer): Boolean = {
    getQuiz(id).exists(_.answer == proposedAnswer.answer)
  }
}

class MyResponder(requestContext: RequestContext) extends Actor with ActorLogging {
  override def receive: Actor.Receive = {
    case QuizCreated =>
      requestContext.complete(StatusCodes.Created)
      killYourself
    case QuizDeleted =>
      requestContext.complete(StatusCodes.OK)
      killYourself
    case QuizAlreadyExists =>
      requestContext.complete(StatusCodes.Conflict)
    case question: Question =>
      requestContext.complete(StatusCodes.OK, question)
      killYourself
    case QuestionNotFound =>
      requestContext.complete(StatusCodes.NotFound)
      killYourself
    case CorrectAnswer =>
      requestContext.complete(StatusCodes.OK)
      killYourself
    case WrongAnswer =>
      requestContext.complete(StatusCodes.NotFound)
      killYourself
    case string:String =>
      requestContext.complete(StatusCodes.OK,string)
      killYourself
  }


  private def killYourself = self ! PoisonPill
}
