package com.cognite.spark.datasource

import com.softwaremill.sttp._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
import org.apache.spark.sql.sources.{BaseRelation, TableScan}
import org.apache.spark.sql.types.{DataTypes, StructType}
import io.circe.generic.auto._
import org.apache.spark.datasource.MetricsSource

case class ThreeDModelRevisionSectorsItem(
    id: Int,
    parentId: Option[Int],
    path: String,
    depth: Int,
    boundingBox: Map[String, Seq[Double]],
    threedFileId: Long,
    threedFiles: Seq[Map[String, Long]])

class ThreeDModelRevisionSectorsRelation(config: RelationConfig, modelId: Long, revisionId: Long)(
    val sqlContext: SQLContext)
    extends BaseRelation
    with CdpConnector
    with TableScan
    with Serializable {
  @transient lazy private val batchSize = config.batchSize.getOrElse(Constants.DefaultBatchSize)
  @transient lazy private val maxRetries = config.maxRetries.getOrElse(Constants.DefaultMaxRetries)

  @transient lazy private val metricsSource = new MetricsSource(config.metricsPrefix)
  @transient lazy private val threeDModelRevisionSectorsRead =
    metricsSource.getOrCreateCounter(s"assets.read")

  import SparkSchemaHelper._

  override val schema: StructType = structType[ThreeDModelRevisionSectorsItem]

  override def buildScan(): RDD[Row] = {
    val baseUrl = baseThreeDModelReviewSectorsUrl(config.project)
    CdpRdd[ThreeDModelRevisionSectorsItem](
      sqlContext.sparkContext,
      (tds: ThreeDModelRevisionSectorsItem) => {
        if (config.collectMetrics) {
          threeDModelRevisionSectorsRead.inc()
        }
        asRow(tds)
      },
      baseUrl,
      baseUrl,
      config.apiKey,
      config.project,
      batchSize,
      maxRetries,
      config.limit
    )
  }

  def baseThreeDModelReviewSectorsUrl(project: String, version: String = "0.6"): Uri =
    uri"${config.baseUrl}/api/$version/projects/$project/3d/models/$modelId/revisions/$revisionId/sectors"
}