package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Graph}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

object GraphBasics extends App {

  implicit val system: ActorSystem = ActorSystem("GraphBasics")

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(x => x + 1) // hard computation
  val multiplier = Flow[Int].map(x => x * 10) // hard computation
  val output = Sink.foreach[(Int, Int)](println)
  val output2 = Sink.foreach[(Int, Int)](x => println(s"The sum is: ${x._1 + x._2}"))
  val output3 = Sink.foreach[(Int, Int)](x => println(s"The diff is: ${x._2 - x._1}"))

  // step 1 - setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder:  GraphDSL.Builder[NotUsed] => // builder = MUTABLE data structure
      import GraphDSL.Implicits._ // brings some nice operators into scope

      // step 2 - add the necessary SHAPES of this graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator
      val zip = builder.add(Zip[Int, Int]) // fan-in operator

      // step 3 - tying up the SHAPES
      input ~> broadcast // input feeds into broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output



      // step 4 - return a closed shape
      ClosedShape // FREEZE builder's shape
      // shape
    } // graph - inert static graph

  ) // runnable graph
//    graph.run() // runnable graph

  /**
   * exercise 1: feed a source into 2 sinks at the same time (hin: use a broadcast)
   */

  val graph2 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))
      val broadcast2 = builder.add(Broadcast[(Int, Int)](2))
      val zip = builder.add(Zip[Int, Int])

      input ~> broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> broadcast2 ~> output2 // implicit port number
                 broadcast2 ~> output3 // it allocates the port numbers in order


      ClosedShape
    }
  )

//  graph2.run()

  import scala.concurrent.duration._
  import scala.language.postfixOps

  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 second)

  val mergeFlow = Flow[(Int, Int)].map {x => (3 * x._2) + (2 *x._1) }
  val balance1 = Flow[Int].filter(_ % 2 == 0)
  val balance2 = Flow[Int].filter(_ % 2 != 0)

  val sink1 = Sink.foreach[Int](x => println(s"Sink1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Sink2: $x"))

  val mergeBalanceGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // EVERYTHING INSIDE THIS SCOPE OPERATES ON SHAPES

      val zip = builder.add(Zip[Int, Int])
      val broadcast = builder.add(Broadcast[Int](2))
      val sink1Shape = builder.add(sink1) // you cannot tie components directly
      val sink2Shape = builder.add(sink2)

      fastSource ~> zip.in0
      slowSource ~> zip.in1

      zip.out ~> mergeFlow ~> broadcast ~> balance1 ~> sink1Shape
                              broadcast ~> balance2 ~> sink2Shape

      ClosedShape
    }
  )

//  mergeBalanceGraph.run()

  val sink3 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink3: $count")
    count + 1
  })

  val balanceGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge;   balance ~> sink3

      ClosedShape
    }
  )

  balanceGraph.run()
}
