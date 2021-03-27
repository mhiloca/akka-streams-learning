package part4_techniques_patterns

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.scaladsl.{Sink, Source}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object IntegratingWithExternalServices extends App {

  implicit val system: ActorSystem = ActorSystem("IntegratingWithExternalServices")
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericExtService[A, B](element: A): Future[B] = ???

  // example simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
    PagerEvent("FastDataPipeline", "Illegal elements in the data pipeline", new Date),
    PagerEvent("AkkaInfra", "A service stopped responding", new Date),
    PagerEvent("SuperFrontEnd", "A button doesn't work", new Date),
  ))

//  object PagerService {
//    private val engineers = List("Daniel", "John", "lady Gaga")
//    private val emails = Map(
//      "Daniel" -> "daniel@rtjvm.com",
//      "John" -> "john@rtjvm.com",
//      "Lady Gaga" -> "ladygaga@rtjvm.com"
//    )
//
//    def processEvent(pagerEvent: PagerEvent) = Future {
//      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 36000)) % engineers.length
//      val engineer = engineers(engineerIndex.toInt)
//      val engineerEmail = emails(engineer)
//
//      // page the engineer
//      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
//      Thread.sleep(1000)
//
//      // return the email of sent notification
//      engineerEmail
//    }
//  }
//
  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
//  val pagedEngineerEmails = infraEvents.mapAsync(parallelism = 1)(event => PagerService.processEvent(event))
//  // guarantees the relative order of elements - if you don't need the order you can use mapAsyncUnordered
  val pagedEmailsSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))

//  pagedEngineerEmails.to(pagedEmailsSink).run()

  class PageActor extends  Actor  with ActorLogging {
    private val engineers = List("Daniel", "John", "lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@rtjvm.com",
      "John" -> "john@rtjvm.com",
      "Lady Gaga" -> "ladygaga@rtjvm.com"
    )

    def processEvent(pagerEvent: PagerEvent) =  {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 36000)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      log.info(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      // return the email of sent notification
      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  implicit val timeout = Timeout(3 seconds)

  val pagerActor = system.actorOf(Props[PageActor], "pagerActor")
  val alternativePagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])

  alternativePagedEngineerEmails.to(pagedEmailsSink).run()

}
