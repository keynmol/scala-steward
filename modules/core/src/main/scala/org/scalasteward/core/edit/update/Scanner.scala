/*
 * Copyright 2018-2022 Scala Steward contributors
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

package org.scalasteward.core.edit.update

import org.scalasteward.core.data.Version
import org.scalasteward.core.edit.update.data.VersionPosition._
import org.scalasteward.core.edit.update.data.{FilePosition, VersionPosition}
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

object Scanner {
  def findVersionPositions(version: Version, content: String): List[VersionPosition] = {
    val it = findSbtModuleId(version, content) ++
      findMillDependency(version, content) ++
      findScalaVal(version, content) ++
      findUnclassified(version, content)
    it.distinctBy(_.filePosition).toList
  }

  private def findSbtModuleId(version: Version, content: String): Iterator[SbtModuleId] =
    sbtModuleIdRegex(version).findAllIn(content).matchData.map { m =>
      val filePosition = filePositionFrom(m, version)
      val before = m.group(1)
      val groupId = m.group(2)
      val artifactId = m.group(3)
      SbtModuleId(filePosition, before, groupId, artifactId)
    }

  private def sbtModuleIdRegex(version: Version): Regex = {
    val v = Regex.quote(version.value)
    raw"""(.*)"(.*)"\s*%{1,3}\s*"(.*)"\s*%\s*"$v"""".r
  }

  private def findMillDependency(version: Version, content: String): Iterator[MillDependency] =
    millDependencyRegex(version).findAllIn(content).matchData.map { m =>
      val filePosition = filePositionFrom(m, version)
      val before = m.group(1)
      val groupId = m.group(2)
      val artifactId = m.group(3)
      MillDependency(filePosition, before, groupId, artifactId)
    }

  private def millDependencyRegex(version: Version): Regex = {
    val ident = """[^:]*"""
    val v = Regex.quote(version.value)
    raw"""(.*)["`]($ident):{1,3}($ident):$v["`;]""".r
  }

  private def findScalaVal(version: Version, content: String): Iterator[ScalaVal] =
    scalaValRegex(version).findAllIn(content).matchData.map { m =>
      val filePosition = filePositionFrom(m, version)
      val name = m.group(2)
      val before = m.group(1)
      ScalaVal(filePosition, name, before)
    }

  private def scalaValRegex(version: Version): Regex = {
    val ident = """[^=]+?"""
    val v = Regex.quote(version.value)
    raw"""(.*)val\s+($ident)\s*=\s*"$v"""".r
  }

  private def findUnclassified(version: Version, content: String): Iterator[Unclassified] = {
    val v = Regex.quote(version.value)
    val regex = raw"""(.*)$v""".r
    regex.findAllIn(content).matchData.map { m =>
      val filePosition = filePositionFrom(m, version)
      val before = m.group(1)
      Unclassified(filePosition, before)
    }
  }

  private def filePositionFrom(m: Match, version: Version): FilePosition = {
    val start = m.start + m.matched.indexOf(version.value)
    val end = start + version.value.length
    FilePosition(start, end)
  }
}
