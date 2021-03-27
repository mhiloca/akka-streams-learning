package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}

object CustomOperators extends App {

  implicit val system: ActorSystem = ActorSystem("CustomOperators")

  // 1 - Custom source which emits random numbers until cancelled

  class RandomNumberGenerator(max: Int) extends GraphStage[/*step 0: define the shape*/SourceShape[Int]] {

    // step 1: define the ports and the component-specific members
    val outPort: Outlet[Int] = Outlet[Int]("randomGenerator")
    val random = new Random()

    // step 2: construct a new shape
    override def shape: SourceShape[Int] = SourceShape(outPort)

    // step 3: create the logic - this below header is pretty standard, you rarely need to change it
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // step 4:
      // define mutable state by setting handlers on our ports
      // implement my logic here

      setHandler(outPort, new OutHandler {
        // when there's demand from downstream
        override def onPull(): Unit = {
          // emit new element
          val nextNumber = random.nextInt(max)
          // push it out of the outPort
          push(outPort, nextNumber)
        }
      })
    }
  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
//  randomGeneratorSource.runWith(Sink.foreach(println))

  // 2 -  custom sink that will print elements in batches of a given size

  class Batcher(batchSize: Int) extends GraphStage[/*step 0: define the shape*/SinkShape[Int]] {
    // step 1 - define the ports and the component-specific members
    val inPort: Inlet[Int] = Inlet[Int]("batcher")

    // step 2: construct a new shape
    override def shape: SinkShape[Int] = SinkShape(inPort)

    // step 3: create the logic
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      override def preStart(): Unit = {
        pull(inPort)
      }

      // define mutable state
      val batch = new mutable.Queue[Int]

      setHandler(inPort, new InHandler {
        // setting handlers for whenever the upstreams wants to send me an element
        override def onPush(): Unit = {
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)

          Thread.sleep(100)

          if (batch.size >=  batchSize)
            println(s"New batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))

          pull(inPort) // send demand upstream
        }

        override def onUpstreamFinish(): Unit = {
          if (batch.nonEmpty) {
            println(s"New batch: " + batch.dequeueAll(_ => true).mkString("[", ",", "]"))
            println("Stream finished")
          }
        }
      })
    }
  }
  val batcherSink = Sink.fromGraph(new Batcher(10))
//  randomGeneratorSource.to(batcherSink).run()

  /**
   * Exercise: custom flow - a simple filter flow
   * - 2 ports: an input port and an output port
   *    need to set handlers for each
   */

  class SimpleFilter[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    val inPort: Inlet[T] = Inlet[T]("filterIn")
    val outPort: Outlet[T] = Outlet[T]("filterOut")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          try {
            val element = grab(inPort)
            if (predicate(element)) {
              push(outPort, element)
            } else pull(inPort)
          } catch {
            case e: Throwable => failStage(e)
          }

        }
      })

      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = {
          pull(inPort)
        }
      })
    }
  }

  val filterEvens = Flow.fromGraph(new SimpleFilter[Int](x => x % 2 == 0))

//  randomGeneratorSource.via(filterEvens).to(batcherSink).run()

  // 3 - a flow that counts the number of elements that go through it
  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {
    val inPort: Inlet[T] = Inlet[T]("counterIn")
    val outPort: Outlet[T] = Outlet[T]("counterOut")

    override val shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]
      val logic = new GraphStageLogic(shape) {
        // setting mutable state
        var counter = 0

        setHandler(outPort, new OutHandler {
          override def onPull(): Unit =
            pull(inPort) // whenever I receive a call from downstream I need to inform upstream

          override def onDownstreamFinish(cause: Throwable): Unit = {
            promise.success(counter)
            super.onDownstreamFinish(cause)
          }
        })

        setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
            // extract the element
            val nextElement = grab(inPort)
            counter += 1
            // pass it on
            push(outPort, nextElement)
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        })

      }
      (logic, promise.future)
    }
  }

  val counterFlow = Flow.fromGraph(new CounterFlow[Int])

  val counterFuture = Source(1 to 10)
    .map(x => if(x == 7) throw new RuntimeException("Gotcha!") else x)
    .viaMat(counterFlow)(Keep.right)
//    .to(Sink.foreach[Int](x => if (x == 7) throw new RuntimeException("Gotcha!") else x))
    .to(Sink.foreach[Int](println))
    .run()

  import system.dispatcher
  counterFuture.onComplete{
    case Success(value) => println(s"This is the total $value")
    case Failure(exception) => println(s"Count stopped because of: $exception")
  }
}
