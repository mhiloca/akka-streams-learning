package part5_advanced

import akka.actor.ActorSystem
import akka.stream.KillSwitches
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}

import scala.concurrent.duration._
import scala.language.postfixOps

object DynamicStreamHandling extends App {

  implicit val system: ActorSystem = ActorSystem("DynamicStreamHandling")

  // #1 Kill Switch

  val killSwitchFLow = KillSwitches.single[Int]
  import system.dispatcher

  val counter = Source(LazyList.from(1)).throttle(1, 1 second).log("counter")
  val sink = Sink.ignore

  val killSwitch = counter
    .viaMat(killSwitchFLow)(Keep.right)
    .toMat(sink)(Keep.left)
//    .run()


//  system.scheduler.scheduleOnce(3 seconds) {
//    killSwitch.shutdown()
//  }

  val anotherCounter = Source(LazyList.from(1)).throttle(2, 1 second).log("anotherCounter")
  val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")
//
//  counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
//  anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
//
//  system.scheduler.scheduleOnce(3 seconds) {
//    sharedKillSwitch.shutdown()
//  }

  // MergeHub

  val dynamicMerge = MergeHub.source[Int]
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run()

  /*
  * what we are doing here is plugging
  * all the following sources to the same consumer
  * */

  // use this sink any time we like
  Source(1 to 10).runWith(materializedSink)
//  counter.runWith(materializedSink)

  // BroadcastHub
  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)

  /*
  * I can plug this source
  * any number of times in any number of graphs
  * to any number of components */

//  materializedSource.runWith(Sink.ignore)
//  materializedSource.runWith(Sink.foreach[Int](println))
  /**
   * Challenge - combine a mergeHub to a broadcastHub
   * A publisher-subscriber component
   */

  val merge = MergeHub.source[String]
  val bcast = BroadcastHub.sink[String]
  val (publisherPort, subscriberPort) = merge.toMat(bcast)(Keep.both).run()

  subscriberPort.runWith(Sink.foreach(e => println(s"I received: $e")))
  subscriberPort.map(string => string.length).runWith(Sink.foreach(n => println(s"I got a number: $n")))

  Source(List("Akka", "is", "awesome")).runWith(publisherPort)
  Source(List("I", "love", "Scala")).run()
  Source.single("Streeeeeams").runWith(publisherPort)

}
