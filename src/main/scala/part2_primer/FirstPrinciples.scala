package part2_primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future


object FirstPrinciples extends App {

  implicit val system: ActorSystem = ActorSystem("FirsPrinciples")

  // source
  val source = Source(1 to 10)
  // sink
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)
//  graph.run()

  // flows transform elements
  val flow = Flow[Int].map(x => x + 1)
  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

//  sourceWithFlow.to(sink).run()
//  source.to(flowWithSink).run()
//  source.via(flow).to(sink).run()

  // nulls are NOT allowed
  val illegalSource = Source.single[Option[String]](Some("hello"))
//  illegalSource.to(Sink.foreach(println)).run()
  // use Options instead

  // various kinds of sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(LazyList.from(1).take(5))

  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.future(Future(42))
//  futureSource.to(Sink.foreach[Int](println)).run()

  // sinks
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[Int](println)
  val headSink = Sink.head[Int] // retrieves head and then closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)


  // flows - usually mapped to collection operators
  val mapFlow = Flow[Int].map(x => 2 * x)
  val takeFlow = Flow[Int].take(5)
  // drop, filter
  // NOT have flatMap

  // source -> flow -> flow -> flow -> ... -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
//  doubleFlowGraph.run()
  // syntactic sugar

  val mapSource = Source(1 to 10).map(x => x *2) // Source(1 to 10).via(Flow[Int].map(x => x* 2))
  // run streams directly
//  mapSource.runForeach(println) // mapSource.to(Sink.foreach(println)).run()

  // OPERATORS = components

  /**
   * Exercise: create a stream that takes the names of persons, then you will keep
   * the first two names with length > 5 chars and print them out to the console
   */

//  val peopleNames = List("Alec", "Ivan", "George", "Gian", "Mhirley", "Caio")
//  val nameSource = Source(peopleNames)
//    .filter(name => name.length > 5) // .filter(_.length > 5)
//    .take(2)
//  nameSource.runForeach(println)

}
