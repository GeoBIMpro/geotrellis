package geotrellis.spark.io.accumulo

import java.util.UUID

import geotrellis.spark._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.index._
import geotrellis.spark.utils._
import geotrellis.spark.io.hadoop._

import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce.Job
import org.apache.hadoop.fs.Path

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import org.apache.accumulo.core.data.{Key, Mutation, Value, Range => ARange}
import org.apache.accumulo.core.client.mapreduce.{AccumuloOutputFormat, AccumuloFileOutputFormat}
import org.apache.accumulo.core.client.BatchWriterConfig

import scala.collection.JavaConversions._

import scalaz.concurrent.Task
import scalaz.stream._


class RDDWriter[K: AvroRecordCodec, TileType: AvroRecordCodec](instance: AccumuloInstance){
  def write(
      raster: RDD[(K, TileType)],
      table: String,
      localityGroup: Option[String],
      keyBounds: KeyBounds[K],
      keyIndex: KeyIndex[K],
      getRowId: Long => String,
      getColFamily: K => String,
      getColQualifier: K => String,
      oneToOne: Boolean = false,
      strategy: AccumuloWriteStrategy): Unit = {
    implicit val sc = raster.sparkContext

    // Create table if it doesn't exist.
    val ops = instance.connector.tableOperations()
    if (! ops.exists(table))
      ops.create(table)

    if (localityGroup.isDefined) {
      val groups = ops.getLocalityGroups(table)
      val newGroup: java.util.Set[Text] = Set(new Text(localityGroup.get))
      ops.setLocalityGroups(table, groups.updated(table, newGroup))
    }
    
    val codec  = KeyValueRecordCodec[K, TileType]

    val encodeKey = (key: K) => {
      new Key(getRowId(keyIndex.toIndex(key)), getColFamily(key), getColQualifier(key))
    }

    val kvPairs: RDD[(Key, Value)] = {
      if (oneToOne)
        raster.map { case row => encodeKey(row._1) -> Vector(row) }
      else
        raster.groupBy { row => encodeKey(row._1) }
    }.map { case (key: Key, pairs) =>
      (key, new Value(AvroEncoder.toBinary(pairs.toVector)(codec)))
    }
    
    strategy match {
      case strategy @ HdfsWriteStrategy(ingestPath) =>
        val job = Job.getInstance(sc.hadoopConfiguration)
        instance.setAccumuloConfig(job)
        val conf = job.getConfiguration
        val outPath = HdfsUtils.tmpPath(ingestPath, UUID.randomUUID.toString, conf)
        val failuresPath = outPath.suffix("-failures")

        HdfsUtils.ensurePathExists(failuresPath, conf)
        kvPairs
          .sortBy{ case (key, _) => key }
          .saveAsNewAPIHadoopFile(
            outPath.toString,
            classOf[Key],
            classOf[Value],
            classOf[AccumuloFileOutputFormat],
            conf)

        val ops = instance.connector.tableOperations()
        ops.importDirectory(table, outPath.toString, failuresPath.toString, true)

        // cleanup ingest directories on success
        val fs = ingestPath.getFileSystem(conf)
        if( fs.exists(new Path(outPath, "_SUCCESS")) ) {
          fs.delete(outPath, true)
          fs.delete(failuresPath, true)
        } else {
          throw new java.io.IOException(s"Accumulo bulk ingest failed at $ingestPath")
        }

      case SocketWriteStrategy(config: BatchWriterConfig) =>
        val BC = KryoWrapper((instance, config))
        kvPairs.foreachPartition { partition =>
          val (instance, config) = BC.value
          val writer = instance.connector.createBatchWriter(table, config)

          val mutations: Process[Task, Mutation] =
            Process.unfold(partition){ iter =>
              if (iter.hasNext) {
                val (key, value) = iter.next()
                val mutation = new Mutation(key.getRow)
                mutation.put(key.getColumnFamily, key.getColumnQualifier, System.currentTimeMillis(), value)
                Some(mutation, iter)
              } else  {
                None
              }
            }

          val writeChannel = channel.lift { (mutation: Mutation) => Task { writer.addMutation(mutation) } }
          val writes = mutations.tee(writeChannel)(tee.zipApply).map(Process.eval)
          nondeterminism.njoin(maxOpen = 32, maxQueued = 32)(writes).run.run
          writer.close()
        }
      }
  }

}

object RDDWriter {
  /**
   * Mapping KeyBounds of Extent to SFC ranges will often result in a set of non-contigrious ranges.
   * The indices exluded by these ranges should not be included in split calcluation as they will never be seen.
   */
  def getSplits[K](kb: KeyBounds[K], ki: KeyIndex[K], count: Int): Seq[Long] = {
    var stack = ki.indexRanges(kb).toList
    def len(r: (Long, Long)) = r._2 - r._1 + 1l
    val total = stack.foldLeft(0l){ (s,r) => s + len(r) }
    val binWidth = total / count

    def splitRange(range: (Long, Long), take: Long): ((Long, Long), (Long, Long)) = {
      assert(len(range) > take)
      assert(take > 0)
      (range._1, range._1 + take - 1) -> (range._1 + take, range._2)
    }

    val arr = Array.fill[Long](count - 1)(0)
    var sum = 0l
    var i = 0

    while (i < count - 1) {
      val nextStep = sum + len(stack.head)
      if (nextStep < binWidth){
        sum += len(stack.head)
        stack = stack.tail
      } else if (nextStep == binWidth) {
        arr(i) = stack.head._2
        stack = stack.tail
        i += 1
        sum = 0l
      } else {
        val (take, left) = splitRange(stack.head, binWidth - sum)
        stack = left :: stack.tail
        arr(i) = take._2
        i += 1
        sum = 0l
      }
    }
    arr
  }
}
