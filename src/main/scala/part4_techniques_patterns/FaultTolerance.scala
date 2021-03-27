package part4_techniques_patterns

import akka.actor.ActorSystem
import akka.stream.{ActorAttributes, RestartSettings}
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import akka.stream.Supervision.{Resume, Stop}

import scala.util.Random

object FaultTolerance extends App {

  implicit val system: ActorSystem = ActorSystem("FaultTolerance")

  // 1- Logging
  val faultySource = Source(1 to 10).map( e => if (e == 6) throw new RuntimeException else e)
//  faultySource.log("tracking elements").to(Sink.ignore).run()

  // 2 - Gracefully terminating a stream
  faultySource.recover {
    case _: RuntimeException => Int.MinValue
  }.log("gracefulSource")
    .to(Sink.ignore)
    .run()

  // 3 - recover with another stream
  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 99)
  }).log("recoverWithRetries")
    .to(Sink.ignore)
//    .run()

  // 4 - backoff supervision
  import scala.concurrent.duration._
  import scala.language.postfixOps
  val restartSource = RestartSource.onFailuresWithBackoff(
    RestartSettings (
      minBackoff = 1 second,
      maxBackoff = 30 seconds,
      randomFactor = 0.2
    )
  )(() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(elem => if (elem == randomNumber) throw new RuntimeException else elem)
  })

  restartSource
    .log("restartBackoff")
    .to(Sink.ignore)
//    .run()

  // 5  - supervision strategy
  val numbers = Source(1 to 20).map(n => if (n == 13) throw new RuntimeException else n).log("supervision")
  val supervisedNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy {
    /*
      Resume = skips the faulty element
      Stop = stop the stream
      Restart = resume + clears the internal state
     */
    case _: RuntimeException => Resume
    case _ => Stop
  })
  supervisedNumbers.to(Sink.ignore).run()
}
