package ml.wolfe.macros

import ml.wolfe._
import ml.wolfe.fg.AndPotential

/**
 * @author Sebastian Riedel
 */
class OptimizeByInferenceSpecs extends WolfeSpec {

  import OptimizedOperators._
  import Wolfe._

  "An argmax operator" should {

    "return the argmax of a one-node sample space and atomic objective" in {
      val actual = argmax(0 until 5) { _.toDouble }
      val expected = BruteForceOperators.argmax(0 until 5) { _.toDouble }
      actual should be(expected)
    }


    "return the argmax of a one-node sample space and atomic objective with implicit domain" in {
      val actual = argmax(bools) { I(_) }
      val expected = BruteForceOperators.argmax(bools) { I(_) }
      actual should be(expected)
    }

    "return the argmax of a two node case class sample space, one observation and atomic objective" in {
      case class Data(x: Boolean, y: Boolean)
      val actual = argmax(Wolfe.all(Data) filter (_.x)) { d => I(!d.x || d.y) }
      val expected = BruteForceOperators.argmax(Wolfe.all(Data) filter (_.x)) { d => I(!d.x || d.y) }
      actual should be(expected)
    }

    "support where instead of filter" in {
      case class Data(x: Boolean, y: Boolean)
      val actual = argmax(Wolfe.all(Data) where (_.x)) { d => I(!d.x || d.y) }
      val expected = BruteForceOperators.argmax(Wolfe.all(Data) filter (_.x)) { d => I(!d.x || d.y) }
      actual should be(expected)
    }

    "return the argmax of a two node case class sample space, one observation and atomic objective (new style)" in {
      case class Data(x: Boolean, y: Boolean)
      val actual = argmax(Wolfe.all(Data) filter (_.x)) { d => I(!d.x || d.y) }
      val expected = BruteForceOperators.argmax(Wolfe.all(Data) filter (_.x)) { d => I(!d.x || d.y) }
      actual should be(expected)
    }

    "return the argmin of function " in {
      val actual = argmin(-3 until 3) { x => x * x}
      val expected = BruteForceOperators.argmin(-3 until 3) { x => x * x}
      actual should be (expected)
    }


    "use the algorithm in the maxBy annotation" in {
      var usedAnnotation = false
      def fun(fg:FactorGraph):Unit = usedAnnotation = true

      @OptimizeByInference(fun)
      def model(b:Boolean):Unit = I(b)
      val b = argmax(bools) {model}

      usedAnnotation should be(true)
    }

    "find the optimal solution of a linear chain" in {
      def space = seqsOfLength(5, Range(0, 3))
      @OptimizeByInference(BeliefPropagation(_, 1))
      def potential(seq: Seq[Int]) = {
        def local = sum (0 until seq.size) (i => i * I(seq(i) == i))
        def pairs = sum (0 until seq.size - 1)(i => I(seq(i) == seq(i + 1)))
        local + pairs
      }
      val actual = argmax(space) { potential }
      val expected = BruteForceOperators.argmax(space) { potential }

      actual should be(expected)
    }

    "use a tailor-made potential" in {

      import FactorGraph._

      case class Sample(x: Boolean, y: Boolean)

      def space = Wolfe.all(Sample)

      def andImpl(arg1: Edge, arg2: Edge) = new AndPotential(arg1, arg2)

      @Potential(andImpl(_: Edge, _: Edge))
      def and(arg1: Boolean, arg2: Boolean) = if (arg1 && arg2) 0.0 else Double.NegativeInfinity

      val actual = argmax(space) { s => and(s.x, s.y) }
      val expected = BruteForceOperators.argmax(space) { s => and(s.x, s.y) }

      actual should be(expected)

    }

    "use a tailor-made potential with structured arguments" in {

      import FactorGraph._

      case class Sample(pred:Pred[Int])

      implicit def ints = 0 until 4

      def space = Wolfe.all(Sample)

      @Potential((args:Seq[Edge]) => new AndPotential(args(0),args(1)))
      def andSeq(args:Seq[Boolean]) = if (args(0) && args(1)) 0.0 else Double.NegativeInfinity

      val actual = argmax(space) { s => andSeq(Seq(s.pred(0), s.pred(1))) }
      val expected = BruteForceOperators.argmax(space) { s => andSeq(Seq(s.pred(0), s.pred(1))) }

      actual should be(expected)

    }

  }

}
