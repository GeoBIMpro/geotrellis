package geotrellis.spark.etl.hadoop

import geotrellis.raster.MultiBandTile
import geotrellis.spark._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.index.KeyIndexMethod
import geotrellis.spark.io.json._

import org.apache.hadoop.fs.Path
import org.apache.spark.SparkContext

class MultibandSpatialHadoopOutput extends HadoopOutput[SpatialKey, MultiBandTile, RasterMetaData] {
  def writer(method: KeyIndexMethod[SpatialKey], props: Parameters)(implicit sc: SparkContext) =
    HadoopLayerWriter(props("path")).keyBoundsComputingWriter[SpatialKey, MultiBandTile, RasterMetaData](method)
}
