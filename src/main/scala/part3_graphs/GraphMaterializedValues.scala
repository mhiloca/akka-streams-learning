package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{FlowShape, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {

  implicit val system: ActorSystem = ActorSystem("GraphMaterializedValues")

  val wordSource = Source(List("Akka", "is", "awesome", "rock", "the", "jvm"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

  /*
    A composite component (that acts like a sink)
    - PRINTS out all strings which are lowercase
    - COUNTS the strings that are short (< 5 chars)
   */

  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(printer, counter)((printerMatValue, counterMatValue) => counterMatValue) { implicit builder =>
      (printerShape, counterShape) =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFilter = builder.add(Flow[String].filter(word => word == word.toLowerCase))
      val shortString = builder.add(Flow[String].filter(_.length < 5))

      broadcast.out(0) ~> lowercaseFilter ~> printerShape
      broadcast.out(1) ~> shortString ~> counterShape

      SinkShape(broadcast.in)
    }
  )

  import system.dispatcher
//  val shortStringsCountFuture = wordSource.toMat(complexWordSink)(Keep.right).run()
//  val shortStringsCountFuture = wordSource.runWith(complexWordSink)

//  shortStringsCountFuture.onComplete {
//    case Success(count) => println(s"The total count of short strings is: $count")
//    case Failure(e) => println(s"Processing was not completed because of: $e")
//  }

  /**
   * Exercise
   */

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink = Sink.fold[Int, B](0)((count, _) => count + 1)
    Flow.fromGraph(GraphDSL.create(counterSink) { implicit builder => counterSinkShape =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[B](2))
      val flowShape = builder.add(flow)

      flowShape ~> broadcast ~> counterSinkShape

      FlowShape(flowShape.in, broadcast.out(1))
    })
  }

  val simpleSource = Source(1 to 42)
  val simpleFlow = Flow[Int].map(x => x)
  val simpleSink = Sink.ignore

  val enhancedFlowCountFuture = simpleSource.viaMat(enhanceFlow(simpleFlow))(Keep.right).toMat(simpleSink)(Keep.left).run()
  enhancedFlowCountFuture.onComplete {
    case Success(count) => println(s"$count elements went through the enhanced flow")
    case Failure(e) => println(s"Could not count elements because of: $e")
  }


}
