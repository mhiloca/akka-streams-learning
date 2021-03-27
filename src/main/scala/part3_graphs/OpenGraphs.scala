package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}

object OpenGraphs extends App {

  implicit val system: ActorSystem = ActorSystem("OpenGraphs")

  /*
    A composite source that concatenates 2 sources
    - emits ALL the elements from the first source
    - then ALL the elements from the second
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  // step 1 - create a component from a graph
  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._ // it gives as the ~> operator

      // step 2: declaring components
      val concat = builder.add(Concat[Int](2))
      /*
        Concat: it takes all the elements from the first component and pushes them ou
                and then it takes all the elements from the second component and pushes them out as well
       */
      // step 3: tying them together
      firstSource ~> concat
      secondSource ~> concat

      // step 4
      SourceShape(concat.out)
    }
  )

//  sourceGraph.to(Sink.foreach[Int](println)).run()

  /*
    complex Sink
   */

  val sink1 = Sink.foreach[Int](x => println(s"Meaningful 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful 2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

//  firstSource.to(sinkGraph).run()

  /**
   * challenge - complex flow?
   * write your own flow that's composed of two other flows
   * - one that add 1 to a number
   * - one that does number * 10
   */

  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // EVERYTHING INSIDE THIS SCOPE OPERATES ON SHAPES

      val incrementerShape = builder.add(incrementer)
      val multiplierShape = builder.add(multiplier)

      incrementerShape ~> multiplierShape

      FlowShape(incrementerShape.in, multiplierShape.out)
    }
  )

//  firstSource.via(flowGraph).to(sink1).run()

  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] = {
    // step 1
    Flow.fromGraph(
      GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>

        // step 2

        val sourceShape = builder.add(source)
        val sinkShape = builder.add(sink)

        // step 3
        // step 4
        FlowShape(sinkShape.in, sourceShape.out)
      }
    )
  }

  val f = Flow.fromSinkAndSourceCoupled(sink1, firstSource)
}
