/*
 * Copyright (c) 2013-2020 Commonwealth Computer Research, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0 which
 * accompanies this distribution and is available at
 * http://www.opensource.org/licenses/apache2.0.php.
 */

package org.geomesa.community.blob.api.handlers

import java.io.File
import java.util
import java.util.{Date, ServiceLoader}

import org.geomesa.community.blob.api.FileHandler
import org.geomesa.community.blob.api.GeoMesaBlobStoreSFT._
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.util.factory.Hints
import org.locationtech.geomesa.curve.TimePeriod
import org.locationtech.geomesa.utils.uuid.{Z3FeatureIdGenerator, Z3UuidGenerator}
import org.locationtech.jts.geom.Geometry
import org.opengis.feature.simple.SimpleFeature

import scala.collection.JavaConversions._

object BlobStoreFileHandler {
  def buildSimpleFeature(file: File, params: Map[String, String]): Option[SimpleFeature] = {
    val handlers = ServiceLoader.load(classOf[FileHandler])

    handlers.find(_.canProcess(file, params)).map(_.buildSimpleFeature(file, params))
  }
}



trait AbstractFileHandler extends BlobStoreSimpleFeatureBuilder with FileHandler with BlobStoreFileName {

  override def buildSimpleFeature(file: File, params: util.Map[String, String]): SimpleFeature = {
    val fileName = getFileName(file, params)
    val geom = getGeometry(file, params)
    val dtg = getDate(file, params)
    buildBlobSimpleFeature(fileName, geom, dtg)
  }

  def getDate(file: File, params: util.Map[String, String]): Date = {
    getDateFromFile(file).orElse(getDateFromParams(params)).getOrElse(new Date())
  }

  def getDateFromFile(file: File): Option[Date] = None

  def getDateFromParams(params: util.Map[String, String]): Option[Date] = None

  def getGeometry(file: File, params: util.Map[String, String]): Geometry = {
    getGeometryFromFile(file).orElse(getGeometryFromParams(params)).getOrElse {
      throw new Exception(s"Could not get Geometry for $file with params $params.")
    }
  }

  def getGeometryFromFile(file: File): Option[Geometry] = None

  def getGeometryFromParams(params: util.Map[String, String]): Option[Geometry] = None
}

trait BlobStoreFileName {

  def getFileNameFromParams(params: util.Map[String, String]): Option[String] = {
    Option(params.get(FilenameFieldName))
  }

  def getFileName(file: File, params: util.Map[String, String]): String = {
    getFileNameFromParams(params).getOrElse(file.getName)
  }

}

trait BlobStoreSimpleFeatureBuilder {
  val builderLocal: ThreadLocal[SimpleFeatureBuilder] = new ThreadLocal[SimpleFeatureBuilder] {
    override def initialValue(): SimpleFeatureBuilder = new SimpleFeatureBuilder(sft).featureUserData(Hints.USE_PROVIDED_FID, java.lang.Boolean.TRUE)
  }

  val featureIdGenerator = new Z3FeatureIdGenerator

  def buildBlobSimpleFeature(fileName: String, geom: Geometry, dtg: Date): SimpleFeature = {
    val z3id = Z3UuidGenerator.createUuid(geom, dtg.getTime, TimePeriod.Week)

    val builder = builderLocal.get()
    builder.set(FilenameFieldName, fileName)
    builder.set(GeomFieldName, geom)
    builder.set(IdFieldName, z3id)
    builder.set(DtgFieldName, dtg)

    builder.buildFeature(z3id.toString)
  }
}