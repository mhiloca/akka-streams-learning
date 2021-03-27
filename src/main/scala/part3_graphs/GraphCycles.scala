package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, OverflowStrategy, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip}


object GraphCycles extends App {

  implicit val system: ActorSystem = ActorSystem("GraphCycles")

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating: $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape

    ClosedShape
  }

  //  RunnableGraph.fromGraph(accelerator).run()
  // graph cycle deadlock

  /*
    Solution 1: MergePreferred - special version of a Merge
                which has a preferential input
                whenever a new element is available on the input
                it will take that element and it will pass it on
                regardless what other elements are available on the
                other inputs
   */

  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating: $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }

  //  RunnableGraph.fromGraph(actualAccelerator).run()

  /*
    Solution 2: buffers
   */

  val bufferedRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Accelerating: $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape.preferred <~ repeaterShape

    ClosedShape
  }

  //  RunnableGraph.fromGraph(bufferedRepeater).run()

  /*
    cycles risk deadlocking
    specially if you have unboundedness in your cycles
    - add bounds to the number of elements

    - boundedness vs liveness
   */

  /**
   * Challenge: create a fan-in shape
   * - two inputs which will be fed with EXACTLY ONE number (1 and 1)
   * - output will emit an INFINITE FIBONACCI SEQUENCE based off those 2 numbers
   */



  val fibonacciGenerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val zipShape = builder.add(Zip[BigInt, BigInt])
    val mergeShape = builder.add(MergePreferred[(BigInt, BigInt)](1))

    val flowShape = builder.add(Flow[(BigInt, BigInt)].map { t =>
      Thread.sleep(100)
      (t._2, t._1 + t._2)
    })

    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(pair => pair._2))

    val broadcastShape = builder.add(Broadcast[(BigInt, BigInt)](2))

    zipShape.out ~> mergeShape ~> flowShape ~> broadcastShape ~> extractLast
    mergeShape.preferred <~ broadcastShape

    UniformFanInShape(extractLast.out, zipShape.in0, zipShape.in1)
  }

  val fiboGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source1 = builder.add(Source.single[BigInt](0))
      val source2 = builder.add(Source.single[BigInt](1))
      val sink = builder.add(Sink.foreach[BigInt](println))

      val fiboShape = builder.add(fibonacciGenerator)

      source1 ~> fiboShape.in(0)
      source2 ~> fiboShape.in(1)
      fiboShape.out ~> sink

      ClosedShape
    }
  )

  fiboGraph.run()

}