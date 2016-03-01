package geotrellis.spark.etl.hadoop

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.index.KeyIndexMethod
import geotrellis.spark.io.json._
import geotrellis.spark.SpaceTimeKey

import org.apache.hadoop.fs.Path
import org.apache.spark.SparkContext

class MultibandSpaceTimeHadoopOutput extends HadoopOutput[SpaceTimeKey, MultiBandTile, RasterMetaData] {
  def writer(method: KeyIndexMethod[SpaceTimeKey], props: Parameters)(implicit sc: SparkContext) =
    HadoopLayerWriter(props("path")).keyBoundsComputingWriter[SpaceTimeKey, MultiBandTile, RasterMetaData](method)
}
