package ml.wolfe.fg20

import ml.wolfe.MoreArrayOps._
import ml.wolfe._

trait Residuals extends EdgeMsgsFG {

  trait Msgs {
    def saveCurrentAsLast()
    def residual(): Double
  }

}

trait BeliefPropagationFG extends Residuals with NodeContentFG {

  final class FactorType(val pot: Pot) extends Factor {

    var discSetting: Array[Int]     = null
    var discObs    : Array[Boolean] = null
    var discDims   : Array[Int]     = null

    def iterateDiscSettings(body: Int => Unit): Unit = {
      updateBuffers()
      TablePotential.allSettings(discDims, discObs)(discSetting)(body)
    }

    private[BeliefPropagationFG] var updated = false

    private def updateBuffers(): Unit = {
      if (!updated) {
        discSetting = Array.ofDim[Int](discEdges.length)
        discObs = Array.ofDim[Boolean](discEdges.length)
        discDims = discEdges.map(_.node.variable.dom.size)
        for (i <- 0 until discEdges.length)
          if (discEdges(i).node.observed) {
            discObs(i) = true
            discSetting(i) = discEdges(i).node.setting
          }
        updated = true
      }
    }
  }

  def createFactor(pot: Pot) = new FactorType(pot)

  type NodeContent = Any

  class DiscNodeContent(var belief: Array[Double])

  class ContNodeContent(var mean: Double = 0.0,
                        var dev: Double = 0.0)

  class DiscMsgs(size: Int) extends Msgs {
    val f2n     = Array.ofDim[Double](size)
    val n2f     = Array.ofDim[Double](size)
    val f2nLast = Array.ofDim[Double](size)
    def saveCurrentAsLast(): Unit = {
      set(f2nLast, f2n)
    }
    def residual() = sqDiff(f2n, f2nLast)
  }

  class ContMsgs() extends Msgs {
    var mean    : Double = 0.0
    var dev     : Double = 0.0
    var lastMean: Double = 0.0
    var lastDev : Double = 0.0
    def saveCurrentAsLast() = {
      lastMean = mean
      lastDev = dev
    }
    def residual() = 0.0
  }

  def createDiscMsgs(variable: DiscVar[Any]) = new DiscMsgs(variable.dom.size)
  def createDiscNodeContent(variable: DiscVar[Any]) = new DiscNodeContent(Array.ofDim[Double](variable.dom.size))
  def createContNodeContent(contVar: ContVar) = new ContNodeContent()
  def createContMsgs(contVar: ContVar) = new ContMsgs()


  def updateN2F(edge: Edge) = {
    edge match {
      case d: DiscEdge =>
        for (i <- d.msgs.n2f.indices)
          d.msgs.n2f(i) = { for (e <- d.node.edges if e != edge) yield e.msgs.f2n(i) }.sum
      case _ =>
    }
  }

  def updateBelief(node: Node) = {
    node match {
      case d: DiscNode =>
        for (i <- d.variable.dom.indices)
          d.content.belief(i) = { for (e <- d.edges) yield e.msgs.f2n(i) }.sum
      case _ =>
    }
  }


}

trait EdgePropagation extends Residuals with Scheduling {

  lazy val scheduled = MPSchedulerImpl.schedule()

  def propagate(maxIterations: Int, eps: Double)(weights: FactorieVector): Unit = {
    var iteration = 0
    var converged = false
    while (iteration < maxIterations && !converged) {
      for (directedEdge <- scheduled) directedEdge.direction match {
        case EdgeDirection.N2F =>
          updateN2F(directedEdge.edge)

        case EdgeDirection.F2N =>
          updateF2N(directedEdge.edge, weights)
      }
      iteration += 1
      converged = residual() < eps
    }
  }

  def residual() = {
    edges.map(_.msgs.residual()).sum
  }


  def updateF2N(edge: Edge, weights: FactorieVector)
  def updateN2F(edge: Edge)

}

object MaxProduct {
  trait Potential extends DiscPotential {
    def discMaxMarginalF2N(edge: BeliefPropagationFG#DiscEdge, weights: FactorieVector)
    def contMaxMarginalF2N(edge: BeliefPropagationFG#ContEdge, weights: FactorieVector): Unit = hasNoContVars
    def maxMarginalExpectationsAndObjective(factor: BeliefPropagationFG#FactorType,
                                            dstExpectations: FactorieVector,
                                            weights: FactorieVector): Double
  }
}


class MaxProduct(val problem: Problem) extends BeliefPropagationFG with EdgePropagation {

  type Pot = MaxProduct.Potential

  def acceptPotential = { case pot: MaxProduct.Potential => pot }

  def updateF2N(edge: Edge, weights: FactorieVector) = {
    edge match {
      case d: DiscEdge => edge.factor.pot.discMaxMarginalF2N(d, weights); d.msgs.saveCurrentAsLast()
      case c: ContEdge => edge.factor.pot.contMaxMarginalF2N(c, weights); c.msgs.saveCurrentAsLast()
    }
  }


  def inferMAP(maxIterations: Int = 10, eps: Double = 0.0001, weights: FactorieVector = new DenseVector(0)): MAPResult = {
    propagate(maxIterations, eps)(weights)
    for (node <- nodes) { updateBelief(node); normalizeBelief(node) }
    val gradient = new SparseVector(1000)
    val score = factors.view.map(f => f.pot.maxMarginalExpectationsAndObjective(f, gradient, weights)).sum
    val discState = problem.discVars.map(v => v -> v.dom(discNodes(v).setting)).toMap[Var[Any], Any]
    val contState = problem.contVars.map(v => v -> contNodes(v).setting)
    val maxMarginals = discNodes.values.map(n => DiscBelief(n.variable) -> Distribution.disc(n.variable.dom, n.content.belief))
    MAPResult(new MapBasedState(discState ++ contState), score, gradient, new MapBasedState(maxMarginals.toMap))
  }

  def normalizeBelief(node: Node) {
    node match {
      case d: DiscNode => maxNormalize(d.content.belief)
    }
  }

}

object SumProduct {
  trait Potential extends DiscPotential {
    def discMarginalF2N(edge: BeliefPropagationFG#DiscEdge, weights: FactorieVector)
    def contMarginalF2N(edge: BeliefPropagationFG#ContEdge, weights: FactorieVector): Unit = hasNoContVars
    def marginalExpectationsAndObjective(factor: BeliefPropagationFG#FactorType,
                                         dstExpectations: FactorieVector,
                                         weights: FactorieVector): Double
  }
}


class SumProduct(val problem: Problem) extends BeliefPropagationFG with EdgePropagation {

  type Pot = SumProduct.Potential

  def acceptPotential = { case pot: SumProduct.Potential => pot }

  def updateF2N(edge: Edge, weights: FactorieVector) = {
    edge match {
      case d: DiscEdge => edge.factor.pot.discMarginalF2N(d, weights); d.msgs.saveCurrentAsLast()
      case c: ContEdge => edge.factor.pot.contMarginalF2N(c, weights); c.msgs.saveCurrentAsLast()
    }
  }

  def inferMarginals(maxIterations: Int = 10, eps: Double = 0.0001, weights: FactorieVector = new DenseVector(0)): MarginalResult = {
    propagate(maxIterations, eps)(weights)
    for (node <- nodes) { updateBelief(node); normalizeBelief(node) }
    val gradient = new SparseVector(1000)
    val logZ = factors.view.map(f => f.pot.marginalExpectationsAndObjective(f, gradient, weights)).sum
    val marginals = discNodes.values.map(n => DiscBelief(n.variable) -> Distribution.disc(n.variable.dom, n.content.belief))
    MarginalResult(logZ, gradient, new MapBasedState(marginals.toMap))
  }

  def normalizeBelief(node: Node): Double = {
    node match {
      case d: DiscNode => normalizeLogProb(d.content.belief)
    }
  }
}





