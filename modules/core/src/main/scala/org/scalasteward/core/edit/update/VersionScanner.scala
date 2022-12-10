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
import org.scalasteward.core.edit.update.data.{Substring, VersionPosition}
import scala.util.matching.Regex

object VersionScanner {
  def findPositions(version: Version, content: String): List[VersionPosition] = {
    val offRegions = findOffRegions(content)
    val it = findSbtDependency(version, content) ++
      findMillDependency(version, content) ++
      findMavenDependency(version, content) ++
      findScalaVal(version, content) ++
      findUnclassified(version, content)
    it.filterNot(p => isInside(p.version.start, offRegions)).distinctBy(_.version.start).toList
  }

  private def findSbtDependency(version: Version, content: String): Iterator[SbtDependency] =
    sbtModuleIdRegex(version).findAllIn(content).matchData.map { m =>
      val versionPos = Substring.Position.fromMatch(m, version.value)
      val before = m.group(1)
      val groupId = m.group(2)
      val artifactId = m.group(3)
      SbtDependency(versionPos, before, groupId, artifactId)
    }

  private def sbtModuleIdRegex(version: Version): Regex = {
    val v = Regex.quote(version.value)
    raw"""(.*)"(.*)"\s*%+\s*"(.*)"\s*%+\s*"$v"""".r
  }

  private def findMillDependency(version: Version, content: String): Iterator[MillDependency] =
    millDependencyRegex(version).findAllIn(content).matchData.map { m =>
      val versionPos = Substring.Position.fromMatch(m, version.value)
      val before = m.group(1)
      val groupId = m.group(2)
      val artifactId = m.group(3)
      MillDependency(versionPos, before, groupId, artifactId)
    }

  private def millDependencyRegex(version: Version): Regex = {
    val ident = """[^:]*"""
    val v = Regex.quote(version.value)
    raw"""(.*)["`]($ident):+($ident):+$v["`;]""".r
  }

  private def findMavenDependency(version: Version, content: String): Iterator[MavenDependency] =
    mavenDependencyRegex(version).findAllIn(content).matchData.map { m =>
      val versionPos = Substring.Position.fromMatch(m, version.value)
      val groupId = m.group(1)
      val artifactId = m.group(2)
      MavenDependency(versionPos, groupId, artifactId)
    }

  private def mavenDependencyRegex(version: Version): Regex = {
    val ident = """[^<]*"""
    val v = Regex.quote(version.value)
    raw"""<groupId>($ident)</groupId>\s*<artifactId>($ident)</artifactId>\s*<version>$v</version>""".r
  }

  private def findScalaVal(version: Version, content: String): Iterator[ScalaVal] =
    scalaValRegex(version).findAllIn(content).matchData.map { m =>
      val versionPos = Substring.Position.fromMatch(m, version.value)
      val before = m.group(1)
      val name = m.group(2)
      ScalaVal(versionPos, before, name)
    }

  private def scalaValRegex(version: Version): Regex = {
    val ident = """[^=]+?"""
    val v = Regex.quote(version.value)
    raw"""(.*)val\s+($ident)\s*=\s*"$v"""".r
  }

  private def findUnclassified(version: Version, content: String): Iterator[Unclassified] = {
    val v = Regex.quote(version.value)
    val regex = raw"""(.*)$v(.?)""".r
    regex.findAllIn(content).matchData.flatMap { m =>
      val versionPos = Substring.Position.fromMatch(m, version.value)
      val before = m.group(1)
      val after = Option(m.group(2))
      val leadingCharIsNoLetterOrDigit = !before.lastOption.exists(_.isLetterOrDigit)
      val trailingCharIsNoLetterOrDigit = !after.flatMap(_.headOption).exists(_.isLetterOrDigit)
      Option.when(leadingCharIsNoLetterOrDigit && trailingCharIsNoLetterOrDigit) {
        Unclassified(versionPos, before)
      }
    }
  }
}
