package cognite.spark.v1

import com.cognite.sdk.scala.common.CdpApiException
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.types.{BooleanType, DoubleType, LongType, StringType, StructField, TimestampType}
import org.scalatest.{FlatSpec, Matchers}
import org.apache.spark.SparkException
import org.apache.spark.sql.Row

class DataPointsRelationTest extends FlatSpec with Matchers with SparkTest {
  val readApiKey = System.getenv("TEST_API_KEY_READ")
  val writeApiKey = System.getenv("TEST_API_KEY_WRITE")

  val valhallTimeSeries = "'VAL_23-FT-92537-04:X.Value'"

  val valhallTimeSeriesId = 3385857257491234L

  "DataPointsRelation" should "use our own schema for data points" taggedAs (ReadTest) in {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .load()
      .where(s"id = $valhallTimeSeriesId")
    assert(df.schema.length == 9)

    assert(
      df.schema.fields.sameElements(Array(
        StructField("id", LongType, nullable = true),
        StructField("externalId", StringType, nullable = true),
        StructField("isString", BooleanType, nullable = false),
        StructField("isStep", BooleanType, nullable = false),
        StructField("unit", StringType, nullable = true),
        StructField("timestamp", TimestampType, nullable = false),
        StructField("value", DoubleType, nullable = false),
        StructField("aggregation", StringType, nullable = true),
        StructField("granularity", StringType, nullable = true)
      )))
  }

  it should "iterate over period longer than limit" taggedAs (ReadTest) in {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("batchSize", "40")
      .option("limit", "100")
      .option("partitions", "1")
      .load()
      .where(s"timestamp > to_timestamp(0) and timestamp < to_timestamp(1790902000) and id = $valhallTimeSeriesId")
    assert(df.count() == 100)

    val df2 = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("batchSize", "40")
      .option("limit", "100")
      .option("partitions", "1")
      .load()
      .where(s"timestamp < to_timestamp(1790902000) and id = $valhallTimeSeriesId")
    assert(df2.count() == 100)
  }

  it should "handle initial data set below batch size" taggedAs (ReadTest) in {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("batchSize", "2000")
      .option("limit", "100")
      .option("partitions", "1")
      .load()
      .where(s"id = $valhallTimeSeriesId")
    assert(df.count() == 100)
  }

  it should "apply limit to each partition" taggedAs ReadTest ignore {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("batchSize", "2000")
      .option("limit", "100")
      .option("partitions", "2")
      .load()
      .where(
        s"timestamp >= to_timestamp(1509528850) and timestamp <= to_timestamp(1557485862) and id = $valhallTimeSeriesId")
    assert(df.count() == 200)

    val df2 = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("batchSize", "2000")
      .option("limit", "100")
      .option("partitions", "3")
      .load()
      .where(
        s"timestamp >= to_timestamp(1509528850) and timestamp <= to_timestamp(1557485862) and id = $valhallTimeSeriesId")
    assert(df2.count() == 300)
  }

  it should "handle initial data set with the same size as the batch size" taggedAs (ReadTest) in {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("batchSize", "100")
      .option("limit", "100")
      .option("partitions", "1")
      .load()
      .where(s"timestamp >= to_timestamp(0) and timestamp <= to_timestamp(1790902000) and id = $valhallTimeSeriesId")
    assert(df.count() == 100)
  }

  it should "test that start/stop time are handled correctly for data points" taggedAs (ReadTest) in {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("partitions", "1")
      .load()
      .where(
        s"timestamp > to_timestamp(1509528850) and timestamp < to_timestamp(1509528860) and id = $valhallTimeSeriesId")
    assert(df.count() == 9)
  }

  it should "handle start/stop time without duplicates when using multiple partitions" taggedAs (ReadTest) ignore {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("partitions", "7")
      .load()
      .where(
        s"timestamp > to_timestamp(1509528850) and timestamp < to_timestamp(1509528860) and id = $valhallTimeSeriesId")
    assert(df.count() == 9)
  }

  it should "support aggregations" taggedAs (ReadTest) in {
    val df1 = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("partitions", "1")
      .load()
      .where(
        s"timestamp >= to_timestamp(1509490000) and timestamp <= to_timestamp(1510358400) and aggregation = 'min' and granularity = '1d' and id = $valhallTimeSeriesId")

    assert(df1.count() == 10)
    val df1Partitions = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .load()
      .where(
        s"timestamp >= to_timestamp(1509490000) and timestamp <= to_timestamp(1510358400) and aggregation = 'max' and granularity = '1d' and id = $valhallTimeSeriesId")
    assert(df1Partitions.count() == 10)
    val df2 = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .load()
      .where(
        s"timestamp >= to_timestamp(1520035200) and timestamp <= to_timestamp(1561507200) and aggregation = 'average' and granularity = '60d' and id = $valhallTimeSeriesId")
    assert(df2.count() == 8)
    val df3 = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .load()
      .sort("timestamp")
      .where(
        s"timestamp >= to_timestamp(1514592000) and timestamp <= to_timestamp(1540512000) and aggregation = 'average' and granularity = '60d' and id = $valhallTimeSeriesId")
    val result = df3.collect()
    assert(result.length == 5)
    assert(result(0).getTimestamp(5).getTime == 1514592000000L)
  }

  it should "shift non-aligned aggregates to correct timestamps" taggedAs ReadTest in {
    val df1 = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("partitions", "1")
      .load()
      .sort("timestamp")
      .where(
        s"timestamp >= to_timestamp(1509490001) and timestamp <= to_timestamp(1510358400) and aggregation = 'max' and granularity = '1d' and id = $valhallTimeSeriesId")
    val results1 = df1.collect()
    assert(results1.length == 10)
    assert(results1(0).getTimestamp(5).getTime == 1509494400000L)
    val df2 = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("partitions", "3")
      .load()
      .sort("timestamp")
      .where(
        s"timestamp >= to_timestamp(1509490001) and timestamp <= to_timestamp(1510358400) and aggregation = 'max' and granularity = '1d' and id = $valhallTimeSeriesId")
    val results2 = df2.collect()
    assert(results2.length == 10)
    assert(results2(0).getTimestamp(5).getTime == 1509494400000L)
  }

  it should "be possible to specify multiple aggregation types in one query" taggedAs (ReadTest) in {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .option("batchSize", "100")
      .option("limit", "1")
      .option("partitions", "1")
      .load()
      .where(
        s"timestamp >= to_timestamp(1508889600) and timestamp <= to_timestamp(1511481600) and aggregation in ('sum', 'average', 'max') and granularity = '30d' and id = $valhallTimeSeriesId")
    assert(df.count() == 3)
    val results: Array[Row] = df.collect()
    val Array(max, avg, sum) = results
    val timeSeriesId = valhallTimeSeriesId
    assert(sum.getLong(0) == timeSeriesId)
    assert(avg.getLong(0) == timeSeriesId)
    assert(max.getLong(0) == timeSeriesId)
    assert(sum.getString(7) == "sum")
    assert(avg.getString(7) == "average")
    assert(max.getString(7) == "max")
    assert(sum.getDouble(6) > avg.getDouble(6))
    assert(avg.getDouble(6) < max.getDouble(6))
  }

  it should "be an error to specify an aggregation without specifying a granularity" taggedAs (ReadTest) in {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .load()
      .where(s"aggregation in ('min') and id = $valhallTimeSeriesId")
    val e = intercept[Exception] {
      df.count()
    }
    e.getCause shouldBe a[RuntimeException]
  }

  it should "be an error to specify a granularity without specifying an aggregation" taggedAs (ReadTest) in {
    val df = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "datapoints")
      .load()
      .where(s"granularity = '30d' and id = $valhallTimeSeriesId")
    val e = intercept[Exception] {
      df.count()
    }
    e.getCause shouldBe a[RuntimeException]
  }

  it should "be an error to specify an invalid granularity" taggedAs (ReadTest) in {
    spark.sparkContext.setLogLevel("OFF") // Removing expected Spark executor Errors from the console
    for (granularity <- Seq("30", "dd", "d30", "1", "0", "1.2d", "1.4y", "1.4seconds")) {
      val df = spark.read
        .format("cognite.spark.v1")
        .option("apiKey", readApiKey)
        .option("type", "datapoints")
        .load()
        .where(
          s"aggregation in ('min') and granularity = '$granularity' and id = $valhallTimeSeriesId")
      val e = intercept[Exception] {
        df.count()
      }
      e.getCause shouldBe a[CdpApiException]
      val cdpApiException = e.getCause.asInstanceOf[CdpApiException]
      assert(cdpApiException.code == 400)
    }
    spark.sparkContext.setLogLevel("WARN")
  }

  it should "accept valid granularity specifications" taggedAs (ReadTest) in {
    for (granularity <- Seq(
        "1d",
        "day",
        "h",
        "hour",
        "m",
        "minute",
        "1hour",
        "2h",
        "20d",
        "13day",
        "7m",
        "7minute")) {
      val df = spark.read
        .format("cognite.spark.v1")
        .option("apiKey", readApiKey)
        .option("type", "datapoints")
        .option("batchSize", "1")
        .option("limit", "1")
        .option("partitions", "1")
        .load()
        .where(
          s"timestamp > to_timestamp(0) and timestamp <= to_timestamp(1510358400) and aggregation in ('max') and granularity = '$granularity' and id = $valhallTimeSeriesId")
      assert(df.count() == 1)
    }
  }

  it should "be possible to write datapoints to CDF using the Spark Data Source " taggedAs WriteTest in {

    val testUnit = "datapoints testing"

    val tsName = "datapoints-insert-testing"

    val sourceTimeSeriesDf = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", readApiKey)
      .option("type", "timeseries")
      .load()
    sourceTimeSeriesDf.createOrReplaceTempView("sourceTimeSeries")

    val destinationTimeSeriesDf = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", writeApiKey)
      .option("type", "timeseries")
      .load()
    destinationTimeSeriesDf.createOrReplaceTempView("destinationTimeSeries")

    val destinationDataPointsDf = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", writeApiKey)
      .option("type", "datapoints")
      .load()
    destinationDataPointsDf.createOrReplaceTempView("destinationDatapoints")

    // Clean up old time series data
    spark.sql(s"""select * from destinationTimeSeries where unit = '$testUnit'""")
      .write
      .format("cognite.spark.v1")
      .option("apiKey", writeApiKey)
      .option("type", "timeseries")
      .option("onconflict", "delete")
      .save()

    // Check that it's gone
    val testTimeSeriesAfterCleanup = retryWhile[Array[Row]]({
      spark.sql(s"""select * from destinationTimeSeries where unit = '$testUnit'""").collect
    }, df => df.length > 0)
    assert(testTimeSeriesAfterCleanup.length == 0)

    // Insert new time series test data
    spark
      .sql(s"""
              |select '' as description,
              |'$tsName' as name,
              |isString,
              |metadata,
              |'$testUnit' as unit,
              |null as assetId,
              |isStep,
              |cast(array() as array<long>) as securityCategories,
              |id+1 as id,
              |'datapoints-testing' as externalId,
              |createdTime,
              |lastUpdatedTime
              |from sourceTimeSeries
              |limit 1
     """.stripMargin)
      .select(sourceTimeSeriesDf.columns.map(col): _*)
      .write
      .insertInto("destinationTimeSeries")

    // Check if post worked
    val initialDescriptionsAfterPost = retryWhile[Array[Row]](
      spark
        .sql(s"""select * from destinationTimeSeries where name = '$tsName'""")
        .collect,
      df => df.length < 1)
    assert(initialDescriptionsAfterPost.length == 1)

    val id = initialDescriptionsAfterPost.head.getLong(8)

    // Insert some datapoints to the new time series
    spark
      .sql(s"""
              |select $id as id,
              |'insert-test-data' as externalId,
              |false as isString,
              |false as isStep,
              |'some unit' as unit,
              |to_timestamp(1509490001) as timestamp,
              |double(1.5) as value,
              |null as aggregation,
              |null as granularity
      """.stripMargin)
      .write
      .insertInto("destinationDatapoints")

    // Check if post worked
    val dataPointsAfterPost = retryWhile[Array[Row]](
      spark
        .sql(s"""select * from destinationDatapoints where id = '$id'""")
        .collect,
      df => df.length < 1)
    assert(dataPointsAfterPost.length == 1)
  }

  it should "be an error to specify an invalid (time series) id" taggedAs (WriteTest) in {
    val destinationDf = spark.read
      .format("cognite.spark.v1")
      .option("apiKey", writeApiKey)
      .option("type", "datapoints")
      .load()
    destinationDf.createOrReplaceTempView("destinationDatapoints")

    spark.sparkContext.setLogLevel("OFF") // Removing expected Spark executor Errors from the console
    val e = intercept[SparkException] {
      spark
        .sql(s"""
                   |select 9999 as id,
                   |"" as externalId,
                   |false as isString,
                   |false as isStep,
                   |'someunit' as unit,
                   |bigint(123456789) as timestamp,
                   |double(1) as value,
                   |null as aggregation,
                   |null as granularity
      """.stripMargin)
        .select(destinationDf.columns.map(col): _*)
        .write
        .insertInto("destinationDatapoints")
    }
    e.getCause shouldBe a[CdpApiException]
    val cdpApiException = e.getCause.asInstanceOf[CdpApiException]
    assert(cdpApiException.code == 400)
    spark.sparkContext.setLogLevel("WARN")
  }
}