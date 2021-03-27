package part1_recap

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ScalaRecap extends App {

  val aCondition: Boolean = false
  def myFunction(x: Int) = {
    // code
    if (x > 4) 42 else 65
  }
  // instructions vs expressions
  // types + type inference

  class Animal
  trait Carnivore {
    def eat(a: Animal): Unit
  }

  object Carnivore

  // generics
  abstract class MyList[+A]

  // method notations
  1 + 2 // infix notation - syntax sugar
  1.+(2)

  // FP
  val anIncrementer: Int => Int = (x: Int) => x + 1
  anIncrementer(1)
  List(1, 2, 3)map(anIncrementer) // List(2, 3, 4)
  // HOFs: map, flatMap nd filter
  // for-comprehensions

  // Monads: Options, Try

  //Pattern matching!
  val unknown: Any = 2
  val order = unknown match {
    case 1 => "first"
    case 2 => "second"
    case _ => "unknown"
  }

  try {
    // code that can throw an exception
    throw new RuntimeException
  } catch {
    case e: Exception => println("I caught one")
  }

  /**
   * Scala advanced
   */

  // Multithreading fundamentals
  import scala.concurrent.ExecutionContext.Implicits.global
  val future = Future {
    // long computation
    // executed on SOME other thread
    42
  }
  //  map, flatMap, filter + other niceties e.g. recover/ recoverWith
  future.onComplete {
    case Success(value) => println(s"I found the meaning of life: $value")
    case Failure(exception) => println(s"I found $exception while searching for the meaning of life")
  } // on SOME thread

  val partialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2 => 65
    case _ => 999
  }
  // based on pattern matching - so if it doesn't find a case, it will throw a MatchError

  // type aliases
  type AkkaReceive = PartialFunction[Any, Unit]
  def receive: AkkaReceive = {
    case 1 => println("hello")
    case _ => println("confused>>>")
  }

  // Implicits
  implicit val timeout = 3000
  def setTimeout(f: () => Unit)(implicit timeout: Int): Unit = f()
  setTimeout(() => println("timeout")) // other arg list injected by the compiler

  // conversion
  // 1) implicit methods
  case class Person(name: String) {
    def greet: String = s"Hi, my name is $name"
  }

  implicit def fromStringToPerson(name: String): Person = Person(name)
  "Mhiloca".greet

  // 2) implicit classes
  implicit class Dog(name: String) {
    def bark = println("Bark!")
  }
  "Lassie".bark
  // new Dog("Lassie").bark

  //implicit organizations
  // local scope
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  List(1, 2, 3).sorted // (numberOrdering) => List(3, 2, 1)

  // imported scope

  // companion objects of the types involved in the call
  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((a, b) => a.name.compareTo(b.name) < 0)
  }

  List(Person("Bob"), Person("Alice")).sorted // List(Person("Alice"), Person("Bob"))
}
