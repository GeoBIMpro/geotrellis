/*
 * Copyright 2016 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.spark.mapalgebra.local

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.mapalgebra._
import geotrellis.raster.mapalgebra.local.Subtract
import geotrellis.util.MethodExtensions

trait SubtractTileCollectionMethods[K] extends MethodExtensions[Seq[(K, Tile)]] {
  /** Subtract a constant value from each cell.*/
  def localSubtract(i: Int) =
    self.mapValues { r => Subtract(r, i) }

  /** Subtract a constant value from each cell.*/
  def -(i: Int) = localSubtract(i)

  /** Subtract each value of a cell from a constant value. */
  def localSubtractFrom(i: Int) =
    self.mapValues { r => Subtract(i, r) }

  /** Subtract each value of a cell from a constant value. */
  def -:(i: Int) = localSubtractFrom(i)

  /** Subtract a double constant value from each cell.*/
  def localSubtract(d: Double) =
    self.mapValues { r => Subtract(r, d) }

  /** Subtract a double constant value from each cell.*/
  def -(d: Double) = localSubtract(d)

  /** Subtract each value of a cell from a double constant value. */
  def localSubtractFrom(d: Double) =
    self.mapValues { r => Subtract(d, r) }

  /** Subtract each value of a cell from a double constant value. */
  def -:(d: Double) = localSubtractFrom(d)

  /** Subtract the values of each cell in each raster. */
  def localSubtract(other: Seq[(K, Tile)]): Seq[(K, Tile)] =
    self.combineValues(other)(Subtract.apply)

  /** Subtract the values of each cell in each raster. */
  def -(other: Seq[(K, Tile)]): Seq[(K, Tile)] = localSubtract(other)

  /** Subtract the values of each cell in each raster. */
  def localSubtract(others: Traversable[Seq[(K, Tile)]]): Seq[(K, Tile)] =
    self.combineValues(others)(Subtract.apply)

  /** Subtract the values of each cell in each raster. */
  def -(others: Traversable[Seq[(K, Tile)]]): Seq[(K, Tile)] = localSubtract(others)
}
