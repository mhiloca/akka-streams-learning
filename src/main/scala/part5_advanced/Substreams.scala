package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Substreams extends App {

  implicit val system: ActorSystem = ActorSystem("Substreams")

  // 1 - grouping a stream by a certain function
  val wordsSource = Source(List("Akka", "is", "amazing", "learning", "substreams"))
  val groups = wordsSource.groupBy(30, word => if (word.isEmpty) "\u0000" else word.toLowerCase().charAt(0))

  groups.to(Sink.fold(0)((count, word) => {
    val newCount = count + 1
    println(s"I just received: $word, count is $newCount")
    newCount
  }))
//    .run()

  // 2 - merge substreams back

  val textSource = Source(List(
    "I love Akka Streams",
    "This is amazing",
    "Learning from Rock the JVM"
  ))

  val totalCharCountFuture = textSource.groupBy(2, string => string.length % 2)
    .map(_.length) // do you expensive computation
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
//    .run()
//
//  import system.dispatcher
//  totalCharCountFuture.onComplete {
//    case Success(value) => println(s"Total char count: $value")
//    case Failure(exception) => println(s"Char computation failed: $exception")
//  }
//
//  // Splitting a stream into substreams, when a condition is met
//
//  val text = "I love Akka Streams\nThis is amazing\nLearning from Rock the JVM"
//  val anotherCharCountFuture = Source(text.toList)
//    .splitWhen(c => c == '\n')
//    .filter(_ != '\n')
//    .map(_ => 1)
//    .mergeSubstreams
//    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
//    .run()
//
//  anotherCharCountFuture. onComplete {
//    case Success(value) => println(s"Total char count: $value")
//    case Failure(exception) => println(s"Char computation failed: $exception")
//  }

  // 4 - FLATTENING
  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(x => Source(x to (3 * x))).runWith(Sink.foreach(println)) // the usual flatMap in collections
//  simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach(println))
}
