package io.prediction.algorithms.scalding.mahout.itemrec

import com.twitter.scalding._

import io.prediction.commons.filepath.{ DataFile, AlgoFile }
import io.prediction.commons.scalding.modeldata.ItemRecScores
import cascading.pipe.joiner.LeftJoin

/**
 * Source:
 *
 * Sink:
 *
 * Description:
 *
 * Required args:
 * --dbType: <string> modeldata DB type (eg. mongodb) (see --dbHost, --dbPort)
 * --dbName: <string> (eg. predictionio_modeldata)
 *
 * --hdfsRoot: <string>. Root directory of the HDFS
 *
 * --appid: <int>
 * --engineid: <int>
 * --algoid: <int>
 * --modelSet: <boolean> (true/false). flag to indicate which set
 *
 * --unseenOnly: <boolean> (true/false). only recommend unseen items if this is true.
 * --numRecommendations: <int>. number of recommendations to be generated
 * --recommendationTime: <long> (eg. 9876543210). recommend items with starttime <= recommendationTime and endtime > recommendationTime
 *
 * Optionsl args:
 * --dbHost: <string> (eg. "127.0.0.1")
 * --dbPort: <int> (eg. 27017)
 *
 * --evalid: <int>. Offline Evaluation if evalid is specified
 * --debug: <String>. "test" - for testing purpose
 *
 * --booleanData: <boolean>. Mahout item rec algo flag for implicit action data
 * --implicitFeedback: <boolean>. Mahout item rec algo flag for implicit action data
 *
 * Example:
 *
 */
class ParallelALSModelConstructor(args: Args) extends Job(args) {

  /**
   * parse args
   */
  val dbTypeArg = args("dbType")
  val dbNameArg = args("dbName")
  val dbHostArg = args.list("dbHost")
  val dbPortArg = args.list("dbPort") map (x => x.toInt)

  val hdfsRootArg = args("hdfsRoot")

  val appidArg = args("appid").toInt
  val engineidArg = args("engineid").toInt
  val algoidArg = args("algoid").toInt
  val evalidArg = args.optional("evalid") map (x => x.toInt)
  val OFFLINE_EVAL = (evalidArg != None) // offline eval mode

  val debugArg = args.list("debug")
  val DEBUG_TEST = debugArg.contains("test") // test mode

  val modelSetArg = args("modelSet").toBoolean

  val unseenOnlyArg = args("unseenOnly").toBoolean
  val numRecommendationsArg = args("numRecommendations").toInt
  val recommendationTimeArg = args("recommendationTime").toLong

  val implicitFeedbackArg = args.optional("implicitFeedback").map(x => x.toBoolean).getOrElse(false)
  // implicit preference flag.
  val IMPLICIT_PREFERENCE = implicitFeedbackArg

  /**
   * source
   */

  val predicted = Tsv(AlgoFile(hdfsRootArg, appidArg, engineidArg, algoidArg, evalidArg, "predicted.tsv"), ('uindex, 'predicted)).read

  val ratingSource = Csv(DataFile(hdfsRootArg, appidArg, engineidArg, algoidArg, evalidArg, "ratings.csv"), ",", ('uindexR, 'iindexR, 'ratingR))

  val seenSource = Csv(DataFile(hdfsRootArg, appidArg, engineidArg, algoidArg, evalidArg, "seen.csv"), ",", ('uindexS, 'iindexS))

  val itemsIndex = Tsv(DataFile(hdfsRootArg, appidArg, engineidArg, algoidArg, evalidArg, "itemsIndex.tsv")).read
    .mapTo((0, 1, 2, 3, 4, 5) -> ('iindexI, 'iidI, 'itypesI, 'starttimeI, 'endtimeI, 'inactive)) { fields: (String, String, String, Long, String, String) =>
      val (iindex, iid, itypes, starttime, endtime, inactive) = fields // itypes are comma-separated String

      val endtimeOpt: Option[Long] = endtime match {
        case "PIO_NONE" => None
        case x: String => {
          try {
            Some(x.toLong)
          } catch {
            case e: Exception => {
              assert(false, s"Failed to convert ${x} to Long. Exception: " + e)
              Some(0)
            }
          }
        }
      }

      val inactiveB: Boolean = inactive match {
        case "PIO_NONE" => false
        case x: String => {
          try {
            x.toBoolean
          } catch {
            case e: Exception => {
              assert(false, s"Failed to convert ${x} to Boolean. Exception: " + e)
              false
            }
          }
        }
      }

      (iindex, iid, itypes.split(",").toList, starttime, endtimeOpt, inactiveB)
    }

  val usersIndex = Tsv(DataFile(hdfsRootArg, appidArg, engineidArg, algoidArg, evalidArg, "usersIndex.tsv")).read
    .mapTo((0, 1) -> ('uindexU, 'uidU)) { fields: (String, String) =>

      fields
    }

  /**
   * sink
   */

  val itemRecScoresSink = ItemRecScores(dbType = dbTypeArg, dbName = dbNameArg, dbHost = dbHostArg, dbPort = dbPortArg, algoid = algoidArg, modelset = modelSetArg)

  /**
   * computation
   */

  val seenRatings = ratingSource.read.mapTo(('uindexR, 'iindexR, 'ratingR) -> ('uindexR, 'iindexR, 'ratingR)) {
    fields: (String, String, Double) => fields // convert score from String to Double
  }

  // convert to (uindex, iindex, rating) format
  // and filter seen items from predicted
  val predictedRating = predicted.flatMap('predicted -> ('iindex, 'rating)) { data: String => parsePredictedData(data) }
    // mahout predicted output may contain items in rating file because it downsample
    // we filter out known rating now because will merge with known rating later
    .joinWithSmaller(('uindex, 'iindex) -> ('uindexR, 'iindexR), seenRatings, joiner = new LeftJoin)
    .filter('ratingR) { r: Double => (r == 0) } // if ratingR == 0, means unseen rating
    .project('uindex, 'iindex, 'rating)

  val combinedRating = if (unseenOnlyArg) {

    val seenActions = seenSource.read.mapTo(('uindexS, 'iindexS) -> ('uindexS, 'iindexS, 'actedS)) {
      fields: (String, String) => (fields._1, fields._2, 1) // add dummy acted field for unseen filtering
    }

    predictedRating
      .joinWithSmaller(('uindex, 'iindex) -> ('uindexS, 'iindexS), seenActions, joiner = new LeftJoin)
      .filter('actedS) { a: Int => (a == 0) } // if actedS == 0, means unseen actions
      .project('uindex, 'iindex, 'rating)

  } else if (IMPLICIT_PREFERENCE) {
    // NOTE: if IMPLICIT_PREFERENCE = true because
    // can't simply merge the seen preference value with predicted preference value due to different meaning in value
    // (depending on which distance function is used).
    // TODO: need special way to handle this case
    predictedRating
  } else {
    // rename for concatenation
    val seenRatings2 = seenRatings.rename(('uindexR, 'iindexR, 'ratingR) -> ('uindex, 'iindex, 'rating))

    predictedRating ++ seenRatings2
  }

  combinedRating
    .joinWithSmaller('iindex -> 'iindexI, itemsIndex)
    .filter('starttimeI, 'endtimeI, 'inactive) { fields: (Long, Option[Long], Boolean) =>
      val (starttimeI, endtimeI, inactive) = fields

      val keepThis: Boolean = (starttimeI, endtimeI) match {
        case (start, None) => (recommendationTimeArg >= start)
        case (start, Some(end)) => ((recommendationTimeArg >= start) && (recommendationTimeArg < end))
        case _ => {
          assert(false, s"Unexpected item starttime ${starttimeI} and endtime ${endtimeI}")
          false
        }
      }
      keepThis && (!inactive)
    }
    .groupBy('uindex) { _.sortBy('rating).reverse.take(numRecommendationsArg) }
    .joinWithSmaller('uindex -> 'uindexU, usersIndex)
    .project('uidU, 'iidI, 'rating, 'itypesI)
    .groupBy('uidU) { _.sortBy('rating).reverse.toList[(String, Double, List[String])](('iidI, 'rating, 'itypesI) -> 'iidsList) }
    .then(itemRecScoresSink.writeData('uidU, 'iidsList, algoidArg, modelSetArg) _)

  /*
  Mahout ItemRec output format
  [24:3.2] => (24, 3.2)
  [8:2.5,0:2.5]  => (8, 2.5), (0, 2.5)
  [0:2.0]
  [16:3.0]
  */
  def parsePredictedData(data: String): List[(String, Double)] = {
    val dataLen = data.length
    data.take(dataLen - 1).tail.split(",").toList.map { ratingData =>
      val ratingDataArray = ratingData.split(":")
      val item = ratingDataArray(0)
      val rating: Double = try {
        ratingDataArray(1).toDouble
      } catch {
        case e: Exception =>
          {
            assert(false, s"Cannot convert rating value of item ${item} to double: " + ratingDataArray + ". Exception: " + e)
          }
          0.0
      }
      (item, rating)
    }
  }
}
