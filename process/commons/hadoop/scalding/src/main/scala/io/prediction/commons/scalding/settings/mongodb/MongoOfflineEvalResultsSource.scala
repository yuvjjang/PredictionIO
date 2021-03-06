package io.prediction.commons.scalding.settings.mongodb

import com.twitter.scalding._

import cascading.pipe.Pipe
import cascading.flow.FlowDef

import java.util.ArrayList
import java.util.HashMap

import com.mongodb.casbah.Imports.MongoDBObject

import io.prediction.commons.scalding.MongoSource
import io.prediction.commons.scalding.settings.OfflineEvalResultsSource
import io.prediction.commons.scalding.settings.OfflineEvalResultsSource.FIELD_SYMBOLS

class MongoOfflineEvalResultsSource(db: String, hosts: Seq[String], ports: Seq[Int]) extends MongoSource(
  db = db,
  coll = "offlineEvalResults",
  cols = {
    val offlineEvalResultsCols = new ArrayList[String]()
    offlineEvalResultsCols.add("_id")
    offlineEvalResultsCols.add("evalid")
    offlineEvalResultsCols.add("metricid")
    offlineEvalResultsCols.add("algoid")
    offlineEvalResultsCols.add("score")
    offlineEvalResultsCols.add("iteration")
    offlineEvalResultsCols.add("splitset")

    offlineEvalResultsCols
  },
  mappings = {
    val offlineEvalResultsMappings = new HashMap[String, String]()
    offlineEvalResultsMappings.put("_id", FIELD_SYMBOLS("id").name)
    offlineEvalResultsMappings.put("evalid", FIELD_SYMBOLS("evalid").name)
    offlineEvalResultsMappings.put("metricid", FIELD_SYMBOLS("metricid").name)
    offlineEvalResultsMappings.put("algoid", FIELD_SYMBOLS("algoid").name)
    offlineEvalResultsMappings.put("score", FIELD_SYMBOLS("score").name)
    offlineEvalResultsMappings.put("iteration", FIELD_SYMBOLS("iteration").name)
    offlineEvalResultsMappings.put("splitset", FIELD_SYMBOLS("splitset").name)

    offlineEvalResultsMappings
  },
  query = MongoDBObject(), // don't support read query
  hosts = hosts,
  ports = ports
) with OfflineEvalResultsSource {

  import com.twitter.scalding.Dsl._ // get all the fancy implicit conversions that define the DSL

  override def getSource: Source = this

  override def writeData(evalidField: Symbol, metricidField: Symbol, algoidField: Symbol, scoreField: Symbol, iterationField: Symbol, splitsetField: Symbol)(p: Pipe)(implicit fd: FlowDef): Pipe = {
    val dataPipe = p.mapTo((evalidField, metricidField, algoidField, scoreField, iterationField, splitsetField) ->
      (FIELD_SYMBOLS("id"), FIELD_SYMBOLS("evalid"), FIELD_SYMBOLS("metricid"), FIELD_SYMBOLS("algoid"), FIELD_SYMBOLS("score"), FIELD_SYMBOLS("iteration"), FIELD_SYMBOLS("splitset"))) {
      fields: (Int, Int, Int, Double, Int, String) =>
        val (evalid, metricid, algoid, score, iteration, splitset) = fields

        val id = evalid + "_" + metricid + "_" + algoid + "_" + iteration + "_" + splitset

        (id, evalid, metricid, algoid, score, iteration, splitset)
    }.write(this)

    dataPipe
  }

}
