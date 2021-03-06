package ml.wolfe.neural

import breeze.linalg.{DenseVector, DenseMatrix}
import ml.wolfe.neural.math.ActivationFunctions


/**
 * Created by mbosnjak on 11/6/14.
 */

trait NetworkLayer {
  require(b.length == W.cols, "b and W don't convey the same number of features: %d vs %d".format(b.length, W.cols))

  var W: DenseMatrix[Double]                              // connection weights
  var b: DenseVector[Double]                              // biases
  var activation: ActivationFunction

//  var activation: (Double => Double)
//  var dactivation: (Double => Double)

  var in: DenseMatrix[Double] = null
  var theta: DenseMatrix[Double] = null   // concat b and W into theta
  var X: DenseMatrix[Double] = null      // Cached outputs before activation
  var XA: DenseMatrix[Double] = null     // Cached outputs after activation
  var grads: DenseMatrix[Double] = null // Cached gradients from backward pass

  val inputSize: Int = W.rows
  val outputSize: Int = W.cols
  val size = W.cols

  def prev: Option[NetworkLayer]
  def next: Option[NetworkLayer]

  def idx: Int

  def gradient(outputs: DenseMatrix[Double], loss: NeuralLossFunction, verbose: Boolean = false): DenseMatrix[Double]

  def numNodes = outputSize

  def output(input: DenseMatrix[Double], verbose: Boolean = false): DenseMatrix[Double] = {
    in = input // Cache input for backward pass
    X = input.t * W
    theta = (input.t * W) + b.toDenseMatrix
    XA = theta.map(activation.f).t
    if (verbose) {
      println("Input (%d rows x %d columns):".format(input.rows, input.cols))
      println(input)
      println
      println("Theta (%d rows x %d columns):".format(theta.rows, theta.cols))
      println(theta)
      println
      println("Output (%d rows x %d columns):".format(XA.rows, XA.cols))
      println(XA)
      println("------------------------")
      println
    }
    XA
  }

  def updateWithCachedGradients(eta: Double = 0.90, verbose: Boolean = false) = {
    if (verbose) {
      println("Computing parameter update with eta = %.2f...".format(eta))
    }
    val wUpdate = (in * grads.t) * eta
    val bUpdate = (grads.t * eta).toDenseVector
    if (verbose) {
      println("Weight Update (%d rows x %d columns):".format(wUpdate.rows, wUpdate.cols))
      println(wUpdate)
      println("Bias Update:")
      println(bUpdate)
      println
    }
    W = W + wUpdate
    b += bUpdate
  }

  def updateWithGradients(gradients: DenseMatrix[Double], verbose: Boolean = false) = {
//    println("Update:\n" + gradients)
//    println("In:\n" + in)
    if (verbose) {
      println("Applying parameter update with provided update...")
    }
    val wUpdate = (in * gradients.t)
    val bUpdate = (gradients.t).toDenseVector
    if (verbose) {
      println("Weight Update (%d rows x %d columns):".format(wUpdate.rows, wUpdate.cols))
      println(wUpdate)
      println("Bias Update:")
      println(bUpdate)
      println
    }
    W = W + wUpdate
    b += bUpdate
  }

  // Direct updates for matrices from BackProp factor
  def forcedUpdate(gradients: DenseMatrix[Double], biasg: DenseVector[Double]) = {
    val gvals = gradients.toDenseVector.toArray
    val m = new DenseMatrix[Double](W.rows, W.cols, gvals)
    println("m:")
    println(m)
    println("W:")
    println(W)
    W = W + m
    b += biasg // (grads.t).toDenseVector
  }
}


/**
 *
 * @param W connection matrix
 * @param b bias vector
 * @param activation activation function
 */
class HiddenLayer(var W: DenseMatrix[Double],
                  var b: DenseVector[Double],
                  var activation: ActivationFunction,
                  pointer: (Int, NeuralNetwork)) extends NetworkLayer {

//                  var activation: (Double => Double),
//                  var dactivation: (Double => Double)) extends NetworkLayer {

//  def gradient(outputs: DenseMatrix[Double], pw: DenseMatrix[Double], loss: NeuralLossFunction, verbose: Boolean = false): DenseMatrix[Double] = {
  def gradient(y: DenseMatrix[Double], loss: NeuralLossFunction, verbose: Boolean = false): DenseMatrix[Double] = {
    if (verbose) {
      println("Computing gradient in hidden layer...")
      println("Desired Outputs (%d rows x %d columns):".format(y.rows, y.cols))
      println(y)
      println
    }
    grads = loss(this, y) //(pw * outputs) :* theta.map(dactivation)
    if (verbose) {
      println("grads = " + y)
    }
    grads
  }

  def idx = pointer._1

  def layers = pointer._2.layers

  def prev: Option[NetworkLayer] = if (idx > 0) Some(layers(idx - 1)) else None

  def next: Option[NetworkLayer] = if (idx <= layers.size) Some(layers(idx + 1)) else None

}

// TODO loss and cost functions?
class OutputLayer(var W: DenseMatrix[Double],
                  var b: DenseVector[Double],
                  var activation: ActivationFunction,
                  pointer: (Int, NeuralNetwork)) extends NetworkLayer {

//                  var activation: (Double => Double),
//                  var dactivation: (Double => Double)) extends NetworkLayer {


  def errors(input: DenseMatrix[Double], y: DenseVector[Double]): Double = ???

  def gradient(y: DenseMatrix[Double], loss: NeuralLossFunction, verbose: Boolean = false): DenseMatrix[Double] = {
    if (verbose) {
      println("Computing gradient in output layer...")
      println("Goal Outputs (%d rows x %d columns):".format(y.rows, y.cols))
      println(y)
      println
    }
    grads = loss(this, y) //(y - theta.map(activation)).t :* theta.map(dactivation)
    if (verbose) {
      println("Gradients (%d rows x %d columns):".format(grads.rows, grads.cols))
      println(grads)
      println
    }
    grads
  }


  def idx = pointer._1

  def layers = pointer._2.layers

  def prev: Option[NetworkLayer] = if (idx > 0) Some(layers(idx - 1)) else None

  def next: Option[NetworkLayer] = if (idx <= layers.size) Some(layers(idx + 1)) else None
}




/*
    println("Propagating forward...")
    println("Input:\n" + input)
    println("W:\n" + W)
    println
    println("input row = " + input.rows)
    for (j <- 0 until W.cols) {
      println("j = " + j)
 //     input * W
    }
    X = DenseMatrix.horzcat(DenseMatrix.ones[Double](input.rows, 1), input)
    println("x = " + X)
    println("Input Dims:\n  R:" + input.rows + "\n  C:" + input.cols)
    println
    println("W Dims:\n  R:" + W.rows + "\n  C:" + W.cols)
    val prod = input.t * W
    println(" Prod = " + prod)
    println(" Bias = " + b)
    val biased = prod + b.toDenseMatrix
    println(" Prod+ = " + biased)
    val mul: DenseMatrix[Double] = X * theta
    mul.map(activation)

    */