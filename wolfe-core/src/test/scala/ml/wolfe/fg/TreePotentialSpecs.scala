package ml.wolfe.fg

import ml.wolfe.FactorGraph.Edge
import ml.wolfe.macros.OptimizedOperators
import ml.wolfe.{BruteForceOperators, Wolfe, WolfeSpec}

/**
 * @author Sebastian Riedel
 */
class TreePotentialSpecs extends WolfeSpec {


  import TreePotential._

  def graph[T](pairs: (T, T)*) = (pairs map (x => x -> true)).toMap withDefaultValue false

  "A projective tree constraint checker" should {
    "disallow a loopy graph" in {
      isFullyConnectedProjectiveTree(graph(0 -> 1, 1 -> 2, 2 -> 0)) should be(false)
    }

    "disallow a disconnected graph" in {
      isFullyConnectedProjectiveTree(graph(0 -> 1, 1 -> 2, 3 -> 4)) should be(false)
    }

    "disallow a legal non-projective tree" in {
      isFullyConnectedProjectiveTree(graph(0 -> 1, 1 -> 3, 3 -> 2, 2 -> 4)) should be(false)
    }

    "allow a legal projective tree" in {
      isFullyConnectedProjectiveTree(graph(0 -> 1, 1 -> 3, 3 -> 2, 3 -> 4)) should be(true)
    }
  }

  "A TreePotential" should {
    "Blah blah blah " in {

      import Wolfe._
      import BruteForceOperators._

      type Node = Int
      type Graph = Pred[(Node,Node)]
      val slen = 3
      val tokens = 0 until slen
      def edges = tokens x tokens
      def graphs = preds(edges)
      def query(graph:Graph) = sum(edges) {e => oneHot(e, I(graph(e)))}

      val result = graphs map (g => treeConstraint(g))
      val marginals = BruteForceOperators.expect(graphs)(treeConstraint[Int])(query)

      def bp = OptimizedOperators.expect(graphs)(treeConstraint[Int])(query)

      println("Yo")
      println(marginals)



    }
  }

}
