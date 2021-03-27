package part3_graphs

import java.util.Date

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, FanOutShape2, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

object MoreOpenGraphs extends App {

  implicit val system: ActorSystem = ActorSystem("MoreOpenGraphs")

  /*
    Example: Max3 Operator
      - 3 inputs of type Int
      - push out the maximum of the three

      for that we are not creating a single runnable graph
      but a static graph and feed it to 3 sources and 1 sink
   */

  // step 1
  val max3StaticGraph = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    // step 2 - define aux= SHAPES
    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

    // step 3
    max1.out ~> max2.in0

    // step4
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source(1 to 10 ).map(_ => 5)
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach[Int](x => println(s"Max is: $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2 - declare SHAPES
      val max3Shape = builder.add(max3StaticGraph)

      // step 3 - tie them together
      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)

      max3Shape.out ~> maxSink

      // step 4
      ClosedShape
    }
  )

//  max3RunnableGraph.run()

  // same for UniformFanOutShape
  // inputs and outputs of the same time

  /*
    Non-uniform fan out shape:

    Processing bank transactions
    Rxn suspicious if amount > 10000

    Streams components for txns
    - output 1: let the txn go through
    - output2: suspicious txn ids
   */

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source(List(
    Transaction("1234568963", "Paul", "Jim", 100 , new Date),
    Transaction("1687415687", "Daniel", "Jim", 1000000 , new Date),
    Transaction("48654183467", "Jim", "Alice", 7000 , new Date)
  ))

  val bankProcess = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](txnId => println(s"Suspicious transaction ID: $txnId"))

  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    // step 2 - define SHAPES
    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(txn => txn.amount > 10000))
    val txnIdExtractor = builder.add(Flow[Transaction].map[String](txn => txn.id))

    // step 3 - tie shapes together
    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIdExtractor

    // step 4
    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtractor.out)
  }

  // step 1
  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 1
      val suspiciousTxnShape = builder.add(suspiciousTxnStaticGraph)

      // step 3
      transactionSource ~> suspiciousTxnShape.in
      suspiciousTxnShape.out0 ~> bankProcess
      suspiciousTxnShape.out1 ~> suspiciousAnalysisService

      ClosedShape
    }
  )

  suspiciousTxnRunnableGraph.run()
}
