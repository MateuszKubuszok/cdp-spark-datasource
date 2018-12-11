val sparkVersion = "2.4.0"
val circeVersion = "0.9.3"
val Specs2Version = "4.2.0"
val artifactory = "https://cognite.jfrog.io/cognite/"

assemblyMergeStrategy in assembly := {
  case "io.netty.versions.properties" => MergeStrategy.first
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

resolvers += "libs-release" at artifactory + "libs-release/"
publishTo := {
  if (isSnapshot.value)
    Some("snapshots" at artifactory + "libs-snapshot-local/")
  else
    Some("releases"  at artifactory + "libs-release-local/")
}

lazy val root = (project in file("."))
  .settings(
    organization := "com.cognite.spark.datasource",
    name := "cdp-spark-datasource",
    version := "0.1.6-SNAPSHOT",
    assemblyJarName in assembly := s"${normalizedName.value}-${version.value}-jar-with-dependencies.jar",
    scalaVersion := "2.11.12",
    scalastyleFailOnWarning := true,
    scalastyleFailOnError := true,
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % Specs2Version % Test,

      "com.softwaremill.sttp" %% "core" % "1.5.0",
      "com.softwaremill.sttp" %% "circe" % "1.5.0",
      "com.softwaremill.sttp" %% "async-http-client-backend-cats" % "1.5.0",

      "org.slf4j" % "slf4j-api" % "1.7.16" % Provided,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-literal" % circeVersion,
      "io.circe" %% "circe-generic-extras" % circeVersion,

      "com.cognite.data" % "cognite-data" % "0.24",

      "org.scalatest" %% "scalatest" % "3.0.5" % Test,

      "com.groupon.dse" % "spark-metrics" % "2.1.0-cognite" % Provided,
      // TODO: check if we really need spark-hive
      "org.apache.spark" %% "spark-hive" % sparkVersion % Provided
        exclude("org.glassfish.hk2.external", "javax.inject"),
      "org.apache.spark" %% "spark-core" % sparkVersion % Provided
        exclude("org.glassfish.hk2.external", "javax.inject"),
      "org.apache.spark" %% "spark-sql" % sparkVersion % Provided
        exclude("org.glassfish.hk2.external", "javax.inject")
    )
  )

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("com.google.protobuf.**" -> "repackaged.com.google.protobuf.@1").inAll,
  ShadeRule.rename("io.circe.**" -> "repackaged.io.circe.@1").inAll,
  ShadeRule.rename("cats.**" -> "repackaged.cats.@1").inAll
)

artifact in (Compile, assembly) := {
  val art = (artifact in (Compile, assembly)).value
  art.withClassifier(Some("assembly"))
}
addArtifact(artifact in (Compile, assembly), assembly)

fork in Test := true
javaOptions ++= Seq("-Xms512M", "-Xmx2048M", "-XX:+CMSClassUnloadingEnabled")
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)