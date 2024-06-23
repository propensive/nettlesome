/*
    Nettlesome, version [unreleased]. Copyright 2024 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package nettlesome

import serpentine.*
import gossamer.*
import rudiments.*
import vacuous.*
import fulminate.*
import contingency.*
import escapade.*
import anticipation.*
import contextual.*
import spectacular.*

import scala.quoted.*

object Scheme:
  given Scheme[Label] is Showable = _.name
  object Http extends Scheme["http"](t"http")
  object Https extends Scheme["https"](t"https")

case class Scheme[+SchemeType <: Label](name: Text)