package ml.wolfe.benchmark

//import org.reflections.Reflections //rockt: not used and results in a compilation error in my IntelliJ
import java.util.UUID
import java.net.InetAddress
import scala.collection.mutable.ArrayBuffer

/**
 * @author Sebastian Riedel
 */
object SimpleBenchmarkRunner {

  //  def main(args: Array[String]) {
  //    import scala.collection.JavaConversions._
  //    if (args.size > 0) System.setProperty("benchmark.runid", args(0))
  //    val reflections = new Reflections("ml.wolfe.benchmark")
  //    val subtypes = reflections.getSubTypesOf(classOf[SimpleBenchmark])
  //    val benchmarks = for (t <- subtypes) yield {
  //      val instance = t.newInstance()
  //      instance
  //    }
  //    for (benchmark <- benchmarks) {
  //      println("Running " + benchmark.getClass.getName)
  //      benchmark.task()
  //    }
  //
  //  }
}

trait SimpleBenchmark {

  private var _task: () => Unit = () => {}

  def task = _task

  def register(t: => Unit) {
    _task = {() => _task; t}
  }

  def measurement(maxWarmUp: Int = 0, times: Int = 1, maxVariance: Double = 0.0)(args: (String, String)*)(snippet: => Double) {
    var last = Double.NegativeInfinity
    var current = snippet
    var warmUp = 0
    while (warmUp < maxWarmUp) {
      last = current
      current = snippet
      warmUp += 1
    }
    var timesCalled = 1
    var total = current
    var values: List[Double] = current :: Nil
    while (timesCalled < times) {
      //      println(current)
      current = snippet
      total += current
      timesCalled += 1
      values = current :: values
    }
    val mean = total / times
    val variance = math.sqrt(values.view.map(v => (v - mean) * (v - mean)).sum / times)
    println(f"Result: ${args.mkString(",")} $mean $variance")
    BenchmarkTools.upload(warmUp, timesCalled, mean, variance, getClass.getName, args: _*)
  }

}

trait BenchmarkSuite {

  private val benchmarks = new ArrayBuffer[Class[Any]]

  def add[B <: SimpleBenchmark](benchmarkClass: Class[B]) {
    benchmarks += benchmarkClass.asInstanceOf[Class[Any]]
  }

  def main(args: Array[String]) {
    if (args.size > 0) System.setProperty("benchmark.runid", args(0))
    if (args.size > 1) System.setProperty("benchmark.context", args(1))
    if (args.size > 2) System.setProperty("benchmark.branch", args(2))

    for (benchmarkClass <- benchmarks) {
      val benchmark = benchmarkClass.newInstance().asInstanceOf[SimpleBenchmark]
      println("Running " + benchmark.getClass.getName)
      benchmark.task()
    }
  }

}

object BenchmarkTools {
  def hostname() = {
    try {
      InetAddress.getLocalHost.getHostName.split("\\.").head.toLowerCase
    } catch {
      case e: Exception => "localhost"
    }
  }

  def upload(warmUp: Int, times: Int, value: Double, variance: Double, className: String, args: (String, String)*) {
    val runId = System.getProperty("benchmark.runid", "run-" + UUID.randomUUID().toString)
    val host = hostname()
    val context = System.getProperty("benchmark.context", host)
    val branch = System.getProperty("benchmark.branch", "unknown")
    println(runId)
    println(host)
    val map = args.toMap
    //println(s"Uploading $warmUp $times $value $variance $args")
    val pre = "https://docs.google.com/forms/d/1dlgYbJzjpzwcQwfkNa5iIJ1bCsCtDZHmBUukQ-M_pqU/formResponse?"
    val params =
      s"entry.505166144=$className&" +
        s"entry.575357901=${map("name")}&" +
        s"entry.96244157=${map("unit")}&" +
        s"entry.305789867=$value&" +
        s"entry.1434152040=$variance&" +
        s"entry.1244078660=Meta&" +
        s"entry.715429054=$runId&" +
        s"entry.2109936603=$host&" +
        s"entry.1214140969=$context&" +
        s"entry.1753801488=$branch"
    val url = pre + params
    try {
      val source = scala.io.Source.fromURL(url)
      val result = source.getLines().mkString("\n")
      println("Uploading to " + url)
      println("Result HTML: " + result.slice(0, 20) + "...")
    } catch {
      case e: Exception =>
        println("Can't upload due to " + e)
    }
  }

  def time(snippet: => Unit) = {
    val start = System.nanoTime()
    snippet
    val end = System.nanoTime()
    end - start
  }
}
