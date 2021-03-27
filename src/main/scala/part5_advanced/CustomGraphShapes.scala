package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ClosedShape, Graph, Inlet, Outlet, Shape}
import com.sun.xml.internal.ws.util.NoCloseOutputStream
import sun.awt.X11.AwtGraphicsConfigData

object CustomGraphShapes extends App {

  implicit val system: ActorSystem = ActorSystem("CustomGraphShapes")

  // balance 2 X 3
  case class Balance2X3 (
                     in0: Inlet[Int],
                     in1: Inlet[Int],
                     out0: Outlet[Int],
                     out1: Outlet[Int],
                     out2: Outlet[Int]
                   ) extends Shape {
    // Inlet[T] 2, Outlet[T] 3
    override val inlets: Seq[Inlet[_]] = List(in0, in1)

    override val outlets: Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2X3(
      in0.carbonCopy(),
      in1.carbonCopy(),
      out0.carbonCopy(),
      out1.carbonCopy(),
      out2.carbonCopy()
    )
  }

  val balance2X3Impl = GraphDSL.create() {
    implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](3))

      merge ~> balance
      Balance2X3(
        merge.in(0),
        merge.in(1),
        balance.out(0),
        balance.out(1),
        balance.out(2)
      )
  }

  import scala.concurrent.duration._
  import scala.language.postfixOps

  val balance2X3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
      val fastSource = Source(LazyList.from(1)).throttle(2, 1 second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
        println(s"[sink $index] Received: $element, current count is $count")
        count + 1
      })

      val sink0 = builder.add(createSink(0))
      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))

      val balance2X3 = builder.add(balance2X3Impl)

      slowSource ~> balance2X3.in0
      fastSource ~> balance2X3.in1

      balance2X3.out0 ~> sink0
      balance2X3.out1 ~> sink1
      balance2X3.out2 ~> sink2

      ClosedShape
    }
  )

//  balance2X3Graph.run()

  /**
   * Exercise: generalize the balance component
   */

  case class BalanceMxN[T](inlets: Seq[Inlet[T]], outlets: Seq[Outlet[T]]) extends Shape {

    override def deepCopy(): Shape = BalanceMxN(
      inlets.map(_.carbonCopy()),
      outlets.map(_.carbonCopy())
    )
  }

  object BalanceMxN {
    def apply[T](nInlets: Int, nOutLets: Int): Graph[BalanceMxN[T], NotUsed] = GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._

        val merge = builder.add(Merge[T](nInlets))
        val balance = builder.add(Balance[T](nOutLets))

        merge ~> balance

        BalanceMxN(merge.inlets, balance.outlets)
    }
  }


  def balanceMxNGraph(inlets: Int, outlets: Int) = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val src0 = Source(LazyList.from(1)).throttle(1, 1 second)
      val src1 = Source(LazyList.from(10)).throttle(2, 1 second)
      val src2 = Source(LazyList.from(20)).throttle(3, 1 second)

      def createSink(i: Int) = Sink.foreach[Int](e => println(s"[sink $i] I received $e"))

      val sink0 = builder.add(createSink(0))
      val sink1 = builder.add(createSink(1))

      val balanceMxN = builder.add(BalanceMxN[Int](inlets, outlets))

      src0 ~> balanceMxN.inlets.head
      src1 ~> balanceMxN.inlets(1)
      src2 ~> balanceMxN.inlets(2)

      balanceMxN.outlets.head ~> sink0
      balanceMxN.outlets(1) ~> sink1

      ClosedShape
    }
  )

  balanceMxNGraph(3, 2).run()

}

