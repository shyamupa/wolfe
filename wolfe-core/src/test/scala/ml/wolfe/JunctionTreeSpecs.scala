package ml.wolfe

import macros.OptimizedOperators
/**
 * @author luke
 */
class JunctionTreeSpecs extends WolfeSpec {
  import OptimizedOperators._
  import Wolfe._
  import macros.Library._

  "Junction Tree Belief Propagation" should {
    def sqDiff(x:Double, y:Double) = (x-y) * (x-y)
    def space = seqsOfLength(5, Seq(0d, 1d, 2d))

    "Return the same argmax as normal BP on a tree" in {
      @OptimizeByInference(BeliefPropagation(_, 1))
      def modelBP(s:Seq[Double]) =  sum(0 until s.length-1) { i:Int => sqDiff(s(i), s(i+1)) }

      @OptimizeByInference(BeliefPropagation.onJunctionTree(_))
      def modelJT(s:Seq[Double]) =  sum(0 until s.length-1) { i:Int => sqDiff(s(i), s(i+1)) }

      val t = argmax(space) {modelBP}
      val u = argmax(space) {modelJT}

      modelBP(t) shouldEqual modelJT(u)
    }

    "Return the same argmax as brute force on a loopy graph" in {
      @OptimizeByInference(BeliefPropagation.onJunctionTree(_)) @OutputFactorGraph
      def model(s:Seq[Double]) =  sum(0 until s.length-1) { i:Int => sqDiff(s(i), s(i+1)) } +
                           2 * sum(0 until s.length-2) { i:Int => sqDiff(s(i), s(i+2)) }

      val t = BruteForceOperators.argmax(space) {model}
      val u = argmax(space) {model}

      model(t) shouldEqual model(u)
    }

  }

}
