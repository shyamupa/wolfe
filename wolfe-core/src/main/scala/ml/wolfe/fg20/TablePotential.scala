package ml.wolfe.fg20

import ml.wolfe.MoreArrayOps._
import ml.wolfe._

import scalaxy.loops._


/**
 * @author Sebastian Riedel
 */
object TablePotential {

  def table(dims: Array[Int], pot: Array[Int] => Double) = {
    val count = dims.product
    val settings = Array.ofDim[Array[Int]](count)
    val scores = Array.ofDim[Double](count)
    for (i <- (0 until count).optimized) {
      val setting = TablePotential.entryToSetting(i, dims)
      val score = pot(setting)
      settings(i) = setting
      scores(i) = score
    }
    Table(settings, scores)
  }

  def apply(vars: Array[DiscVar[Any]], pot: Array[Int] => Double) = {
    val dims = vars.map(_.dom.size)
    new TablePotential(vars, table(dims, pot).scores)
  }

  def apply(vars: Array[DiscVar[Any]], table: Table) = {
    new TablePotential(vars, table.scores)
  }


  //todo: move these both into Util, to share code with LabelledTensor
  /**
   * Turns a setting vector into an entry number.
   * @param setting setting
   * @param dims dimensions of each variable.
   * @return the entry corresponding to the given setting.
   */
  final def settingToEntry(setting: Seq[Int], dims: Array[Int]) = {
    var result = 0
    for (i <- (0 until dims.length).optimized) {
      result = setting(i) + result * dims(i)
    }
    result
  }

  final def settingToEntry(setting: Iterator[Int], dims: Array[Int]) = {
    var result = 0
    var i = 0
    while (i < dims.length) {
      result = setting.next() + result * dims(i)
      i += 1
    }
    result
  }



  def allSettings(dims: Array[Int], observations: Array[Boolean])(target: Array[Int])(body: Int => Unit): Unit = {
    val length = target.length
    var settingId = 0
    var settingMultiplier = 1
    var index = length - 1
    //set observations
    while (index >= 0) {
      if (observations(index)) {
        settingId += settingMultiplier * target(index)
      } else target(index) = 0
      settingMultiplier *= dims(index)
      index -= 1
    }
    settingMultiplier = 1
    index = length - 1
    while (index >= 0) {
      //call body on current setting
      body(settingId)
      //go back to the first element that hasn't yet reached its dimension, and reset everything until then
      while (index >= 0 && (target(index) == dims(index) - 1 || observations(index))) {
        if (!observations(index)) {
          settingId -= settingMultiplier * target(index)
          target(index) = 0
        }
        settingMultiplier *= dims(index)
        index -= 1
      }
      //increase setting by one if we haven't yet terminated
      if (index >= 0) {
        target(index) += 1
        settingId += settingMultiplier
        //depending on where we are in the array we bump up the settingId
        if (index < length - 1) {
          settingMultiplier = 1
          index = length - 1
        }
      }
    }
  }


  /**
   * Turns an entry into a setting
   * @param entry the entry number.
   * @param dims dimensions of the variables.
   * @return a setting array corresponding to the entry.
   */
  final def entryToSetting(entry: Int, dims: Array[Int]) = {
    val result = Array.ofDim[Int](dims.length)
    var current = entry
    for (i <- (0 until dims.length).optimized) {
      val value = current % dims(dims.length - i - 1)
      result(dims.length - i - 1) = value
      current = current / dims(dims.length - i - 1)
    }
    result
  }


}

case class Table(settings: Array[Array[Int]], scores: Array[Double])


final class TablePotential(val discVars: Array[DiscVar[Any]], table: Array[Double]) extends MaxProduct.Potential
                                                                                            with SumProduct.Potential {

  val dims = discVars.map(_.dom.size)

  def score(factor: FG#Factor, weights: FactorieVector) = {
    val entry = TablePotential.settingToEntry(factor.discEdges.iterator.map(_.node.setting), dims)
    table(entry)
  }


  def discMaxMarginalF2N(edge: BeliefPropagationFG#DiscEdge, weights: FactorieVector) = {
    val m = edge.msgs
    val factor = edge.factor

    fill(m.f2n, Double.NegativeInfinity)

    factor.iterateDiscSettings { i =>
      var score = table(i)
      val varValue = factor.discSetting(edge.index)
      for (j <- 0 until discVars.size; if j != edge.index) {
        score += edge.factor.discEdges(j).msgs.n2f(factor.discSetting(j))
      }
      m.f2n(varValue) = math.max(score, m.f2n(varValue))
    }
    maxNormalize(m.f2n)
  }


  def discMarginalF2N(edge: BeliefPropagationFG#DiscEdge, weights: FactorieVector) = {
    val m = edge.msgs
    val factor = edge.factor

    fill(m.f2n, 0.0)

    factor.iterateDiscSettings { i =>
      var score = table(i)
      val varValue = factor.discSetting(edge.index)
      for (j <- 0 until discVars.length; if j != edge.index) {
        score += edge.factor.discEdges(j).msgs.n2f(factor.discSetting(j))
      }
      m.f2n(varValue) = m.f2n(varValue) + math.exp(score)
    }
    //normalize
    normalize(m.f2n)
    //convert to log space
    log(m.f2n)
  }


  def penalizedScore(factor: BeliefPropagationFG#Factor, settingId: Int, setting: Array[Int]): Double = {
    var score = table(settingId)
    for (j <- 0 until factor.discEdges.length) {
      score += factor.discEdges(j).msgs.n2f(setting(j))
    }
    score
  }


  def statsForCurrentSetting(factor: FG#Factor) = isNotLinear


  def maxMarginalExpectationsAndObjective(factor: BeliefPropagationFG#FactorType,
                                          dstExpectations: FactorieVector,
                                          weights: FactorieVector) = {
    // 1) go over all states, find max with respect to incoming messages
    var norm = Double.NegativeInfinity
    var maxScore = Double.NegativeInfinity

    factor.iterateDiscSettings { i =>
      val score = penalizedScore(factor, i, factor.discSetting)
      if (score > norm) {
        norm = score
        maxScore = table(i)
      }
    }
    maxScore
  }

  override def marginalExpectationsAndObjective(factor: BeliefPropagationFG#FactorType,
                                                dstExpectations: FactorieVector,
                                                weights: FactorieVector) = {
    var localZ = 0.0

    //calculate local partition function
    factor.iterateDiscSettings { i =>
      val score = penalizedScore(factor, i, factor.discSetting)
      localZ += math.exp(score)
    }

    var linear = 0.0
    var entropy = 0.0
    //calculate linear contribution to objective and entropy
    factor.iterateDiscSettings { i =>
      val score = penalizedScore(factor, i, factor.discSetting)
      val prob = math.exp(score) / localZ
      linear += table(i) * prob
      entropy -= math.log(prob) * prob
    }
    val obj = linear + entropy
    obj
  }

  def isLinear = false
}