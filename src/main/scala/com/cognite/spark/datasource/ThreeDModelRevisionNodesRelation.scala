package com.cognite.spark.datasource

import com.cognite.spark.datasource.SparkSchemaHelper._
import com.softwaremill.sttp.{Uri, _}
import io.circe.generic.auto._
import org.apache.spark.groupon.metrics.UserMetricsSystem
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SQLContext}

case class ModelRevisionNodeItem(id: Long,
                                 treeIndex: Long,
                                 parentId: Option[Long],
                                 depth: Long,
                                 name: String,
                                 subtreeSize: Long,
                                 metadata: Map[String, String],
                                 boundingBox: Option[Map[String, Seq[Double]]],
                                 sectorId: Option[Long])

class ThreeDModelRevisionNodesRelation(apiKey: String,
                                       project: String,
                                       modelId: Long,
                                       revisionId: Long,
                                       limit: Option[Int],
                                       batchSizeOption: Option[Int],
                                       maxRetriesOption: Option[Int],
                                       metricsPrefix: String,
                                       collectMetrics: Boolean)
                                      (@transient val sqlContext: SQLContext)
  extends BaseRelation
    with TableScan
    with CdpConnector
    with Serializable {
  @transient lazy private val batchSize: Int = batchSizeOption.getOrElse(Constants.DefaultBatchSize)
  @transient lazy private val maxRetries = maxRetriesOption.getOrElse(Constants.DefaultMaxRetries)

  @transient lazy private val modelRevisionNodesRead =
    UserMetricsSystem.counter(s"${metricsPrefix}3dmodelrevisionnodes.read")

  override def schema: StructType = structType[ModelRevisionNodeItem]

  override def buildScan(): RDD[Row] = {
    val baseUrl = base3dModelRevisionMappingsUrl(project)
    CdpRdd[ModelRevisionNodeItem](sqlContext.sparkContext,
      (e: ModelRevisionNodeItem) => {
        if (collectMetrics) {
          modelRevisionNodesRead.inc()
        }
        asRow(e)
      },
      baseUrl, baseUrl, apiKey, project, batchSize, maxRetries, limit)
  }

  def base3dModelRevisionMappingsUrl(project: String, version: String = "0.6"): Uri = {
    uri"https://api.cognitedata.com/api/$version/projects/$project/3d/models/$modelId/revisions/$revisionId/nodes"
  }
}
