package part2_primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}


object MaterializingStreams extends App {

  implicit val system: ActorSystem = ActorSystem("MaterializingStreams")

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
  //  val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int]((a, b) => a + b)
  val sumFuture = source.runWith(sink)

  import system.dispatcher

  sumFuture.onComplete {
    case Success(value) => println(s"The some of all values is $value")
    case Failure(e) => println(s"The sum of the elements could not be computed: $e")
  }

  // choosing materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleSink = Sink.foreach[Int](println)
  //  simpleSource.viaMat(simpleFlow)((sourceMat, flowMat) => flowMat)
  val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)

  graph.run().onComplete {
    case Success(_) => println("Stream processing finished")
    case Failure(e) => println(s"Stream processing failed with $e")
  }

  // sugars
  val sum = Source(1 to 10).runWith(Sink.reduce[Int](_ + _)) // source.to(Sink.reduce)(Keep.right)
  Source(1 to 10).reduce[Int](_ + _) // syntactic sugar for the expression above

  // backwards
  Sink.foreach[Int](println).runWith(Source.single[Int](42)) // source(...).to(sink...)run()
  /*
    because the to and via keeps the left most materialized value, which in this case is the source one
   */

  Flow[Int].map(_ * 2).runWith(simpleSource, simpleSink)

  /**
   * - return the last element out of a source (use Sink.last)
   *  - compute the total word count out of a stream of sentences
   *    - map, fold, reduce
   */


  val testSource = Source(1 to 10)
  val testFlow = Flow[Int].filter(_ % 2 == 0)
  val testSink = Sink.last[Int]

  // 1.1) viaMat and toMat
  val testGraph = testSource.viaMat(testFlow)(Keep.right).toMat(testSink)(Keep.right)
  val testResult = testGraph.run()
  testResult.onComplete {
    case Success(value) => println(s"testResult1 finished running: $value")
    case Failure(e) => println(s"testResult1 not finished because of: $e")
  }

  // 1.2) syntactic sugar
  val testResult2 = testSource.filter(_ % 2 == 0).runWith(testSink)
  testResult2.onComplete {
    case Success(value) => println(s"testResult2 finished running: $value")
    case Failure(e) => println(s"testResult2 not finished because of: $e")
  }

  val f1 = Source(1 to 10).toMat(Sink.last)(Keep.right).run()
  val f2 = Source(1 to 10).runWith(Sink.last)

  val sentences = List(
    "I love to code",
    "I am so very much enjoying to learn Akka",
    "My goal is to become a skillful Akka and Scala programmer"
  )
  val res = Source(sentences)
    .map(str => str.split(" ").length)
    .runWith(Sink.reduce[Int]((a, b) => a + b))

  res.onComplete {
    case Success(value) => println(s"The total word count is: $value")
    case Failure(e) => println(s"Could not compute the total word count, because of: $e")
  }

  val sentenceSource = Source(sentences)

  val wordCountSink = Sink.fold[Int, String](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  val g1 = sentenceSource.toMat(wordCountSink)(Keep.right).run()
  val g2 = sentenceSource.runWith(wordCountSink)
  val g3 = sentenceSource.runFold(0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)

  val wordCountFlow = Flow[String].fold[Int](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  val g4 = sentenceSource.via(wordCountFlow).toMat(Sink.head)(Keep.right).run()
  val g5 = sentenceSource.viaMat(wordCountFlow)(Keep.left).toMat(Sink.head)(Keep.right).run()
  val g6 = sentenceSource.via(wordCountFlow).runWith(Sink.head)
  val g7 = wordCountFlow.runWith(sentenceSource, Sink.head)._2
 }