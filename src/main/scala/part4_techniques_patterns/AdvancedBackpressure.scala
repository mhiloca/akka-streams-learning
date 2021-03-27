package part4_techniques_patterns

import java.util.Date

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

object AdvancedBackpressure extends App {

  implicit val system: ActorSystem = ActorSystem("AdvancedBackpressure")

  // control backpressure
  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Service discovery failed", new Date),
    PagerEvent("Illegal elements in the pipeline", new Date),
    PagerEvent("Number of HTTP 500 spiked", new Date),
    PagerEvent("S service stopped responding", new Date)
  )

  val eventSource = Source(events)

  val onCallEngineer =  "daniel@rtjvm.com" // a fast service for fetching on call emails

  def sendEmail(notification: Notification) = {
    println(s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}") // actually send an email
  }

  val notificationSink = Flow[PagerEvent].map(event => Notification(onCallEngineer, event))
    .to(Sink.foreach[Notification](sendEmail))

//  eventSource.to(notificationSink).run()

  /*
    un-backpressurable source
   */

  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    println(s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}")
  }

  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((event1, event2) => {
      val nInstances = event1.nInstances + event2.nInstances
      PagerEvent(s"You have $nInstances events that require your attentions", new Date, nInstances)
  })
    .map(resultingEvent => Notification(onCallEngineer, resultingEvent))

  // alternative to backpressure
//  eventSource.via(aggregateNotificationFlow).async
//    .to(Sink.foreach[Notification](sendEmailSlow))
//    .run()

  /*
    Slow producers: Extrapolate/expand
   */

  import scala.concurrent.duration._
  import scala.language.postfixOps
  val slowCounter = Source(LazyList.from(1)).throttle(1, 1 second)
  val hungrySink = Sink.foreach[Int](println)

  val extrapolator = Flow[Int].extrapolate(elem => Iterator.from(elem))
  val repeater = Flow[Int].extrapolate(elem => Iterator.continually(elem))

//  slowCounter.via(repeater).to(hungrySink).run()

  val expander = Flow[Int].expand(elem => Iterator.from(elem))

  slowCounter.via(expander).to(hungrySink).run()

  val testSource = Source.cycle(() => List(1, 2, 3, 4, 5).iterator)
    .throttle(10, 1 second)
    .conflate((acc, el) => acc + el)
    .throttle(1, 1 second)

//  testSource.to(Sink.foreach[Int](println)).run()
}
