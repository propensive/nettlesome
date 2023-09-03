/*
    Nettlesome, version [unreleased]. Copyright 2023 Jon Pretty, Propensive OÜ.

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
import fulminate.*
import symbolism.*
import perforate.*
import escapade.*
import iridescence.*
import anticipation.*
import contextual.*
import spectacular.*

object HostnameError:
  enum Reason:
    case LongDnsLabel(label: Text)
    case LongHostname
    case InvalidChar(char: Char)
    case EmptyDnsLabel(n: Int)
    case InitialDash(label: Text)

  object Reason:
    given MessageShow[Reason] =
      case LongDnsLabel(label) => msg"the DNS label $label is longer than 63 characters"
      case LongHostname        => msg"the hostname is longer than 253 characters"
      case InvalidChar(char)   => msg"the character $char is not allowed in a hostname"
      case EmptyDnsLabel(n)    => msg"a DNS label cannot be empty"
      case InitialDash(label)  => msg"the DNS label $label begins with a dash which is not allowed"

import HostnameError.Reason.*

case class HostnameError(reason: HostnameError.Reason)
extends Error(msg"the hostname is not valid because $reason")

case class EmailAddressError() extends Error(msg"the email address was not valid")

case class UrlError(text: Text, offset: Int, expected: UrlError.Expectation)
extends Error(msg"the URL $text is not valid: expected $expected at $offset")

object DnsLabel:
  given show: Show[DnsLabel] = _.text

case class DnsLabel(text: Text)

object Hostname:
  given Show[Hostname] = _.parts.map(_.show).join(t".")
  
  def parse(text: Text): Hostname raises HostnameError =
    val buffer: StringBuilder = StringBuilder()

    def recur(index: Int, parts: List[DnsLabel]): Hostname = safely(text(index)) match
      case '.' | Unset =>
        val label = buffer.toString.tt
        if label.empty then raise(HostnameError(EmptyDnsLabel(parts.length)))(())
        if label.length > 63 then raise(HostnameError(LongDnsLabel(label)))(())
        if label.starts(t"-") then raise(HostnameError(InitialDash(label)))(())
        val parts2 = DnsLabel(label) :: parts
        buffer.clear()
        if index < text.length then recur(index + 1, parts2) else
          if parts2.map(_.text.length + 1).sum > 254 then raise(HostnameError(LongHostname))(())
          Hostname(parts2.reverse*)
      
      case char: Char =>
        if char == '-' || ('A' <= char <= 'Z') || ('a' <= char <= 'z') || char.isDigit
        then buffer.append(char)
        else raise(HostnameError(InvalidChar(char)))(())
        recur(index + 1, parts)
    recur(0, Nil)

case class Hostname(parts: DnsLabel*) extends Shown[Hostname]

object Scheme:
  given Show[Scheme] = _.name
  object Http extends Scheme(t"http")
  object Https extends Scheme(t"https")

case class Scheme(name: Text)

case class Raw(text: Text)

object UrlInput:
  given Substitution[UrlInput, Text, "x"] = UrlInput.Textual(_)
  given Substitution[UrlInput, Raw, "x"] = raw => UrlInput.RawTextual(raw.text)
  given Substitution[UrlInput, Int, "80"] = UrlInput.Integral(_)

enum UrlInput:
  case Integral(value: Int)
  case Textual(value: Text)
  case RawTextual(value: Text)

object UrlInterpolator extends contextual.Interpolator[UrlInput, Text, Url]:
  def complete(value: Text): Url =
    try throwErrors(Url.parse(value)) catch
      case err: UrlError      => throw InterpolationError(Message(err.message.text))
      case err: HostnameError => throw InterpolationError(Message(err.message.text))
  
  def initial: Text = t""
  
  def insert(state: Text, value: UrlInput): Text =
    value match
      case UrlInput.Integral(port) =>
        if !state.ends(t":")
        then throw InterpolationError(msg"a port number must be specified after a colon")
        
        try throwErrors(Url.parse(state+port.show))
        catch
          case err: UrlError      => throw InterpolationError(Message(err.message.text))
          case err: HostnameError => throw InterpolationError(Message(err.message.text))
        
        state+port.show
      
      case UrlInput.Textual(txt) =>
        if !state.ends(t"/")
        then throw InterpolationError(msg"a substitution may only be made after a slash")
        
        try throwErrors(Url.parse(state+txt.urlEncode))
        catch
          case err: UrlError      => throw InterpolationError(Message(err.message.text))
          case err: HostnameError => throw InterpolationError(Message(err.message.text))
        
        state+txt.urlEncode
      
      case UrlInput.RawTextual(txt) =>
        if !state.ends(t"/")
        then throw InterpolationError(msg"a substitution may only be made after a slash")

        try throwErrors(Url.parse(state+txt.urlEncode))
        catch
          case err: UrlError      => throw InterpolationError(Message(err.message.text))
          case err: HostnameError => throw InterpolationError(Message(err.message.text))
        
        state+txt
  
  override def substitute(state: Text, sub: Text): Text = state+sub

  def parse(state: Text, next: Text): Text =
    if !state.empty && !(next.starts(t"/") || next.empty)
    then throw InterpolationError(msg"a substitution must be followed by a slash")
    
    state+next
  
  def skip(state: Text): Text = state+t"1"

object Url:
  given GenericUrl[Url] = _.show
  given (using Raises[UrlError], Raises[HostnameError]): SpecificUrl[Url] = Url.parse(_)
  given GenericHttpRequestParam["location", Url] = show(_)
  given (using Raises[UrlError], Raises[HostnameError]): Decoder[Url] = parse(_)
  given Encoder[Url] = _.show
  given Debug[Url] = _.show

  given Reachable[Url, "", (Scheme, Maybe[Authority])] with
    def separator(url: Url): Text = t"/"
    def descent(url: Url): List[PathName[""]] = url.path
    def root(url: Url): (Scheme, Maybe[Authority]) = (url.scheme, url.authority)
    
    def prefix(root: (Scheme, Maybe[Authority])): Text =
      t"${root(0).name}:${root(1).mm(t"//"+_.show).or(t"")}"
    
  given PathCreator[Url, "", (Scheme, Maybe[Authority])] with
    def path(ascent: (Scheme, Maybe[Authority]), descent: List[PathName[""]]): Url =
      Url(ascent(0), ascent(1), descent.reverse.map(_.render).join(t"/"))
    
  given show: Show[Url] = url =>
    val auth = url.authority.fm(t"")(t"//"+_.show)
    val rest = t"${url.query.fm(t"")(t"?"+_)}${url.fragment.fm(t"")(t"#"+_)}"
    t"${url.scheme}:$auth${url.pathText}$rest"
  
  given ansiShow: Display[Url] = url => out"$Underline(${colors.DeepSkyBlue}(${show(url)}))"

  given asMessage: MessageShow[Url] = url => Message(show(url))

  given action: GenericHtmlAttribute["action", Url] with
    def name: Text = t"action"
    def serialize(url: Url): Text = url.show
  
  given codebase: GenericHtmlAttribute["codebase", Url] with
    def name: Text = t"codebase"
    def serialize(url: Url): Text = url.show
  
  given cite: GenericHtmlAttribute["cite", Url] with
    def name: Text = t"cite"
    def serialize(url: Url): Text = url.show
  
  given data: GenericHtmlAttribute["data", Url] with
    def name: Text = t"data"
    def serialize(url: Url): Text = url.show

  given formaction: GenericHtmlAttribute["formaction", Url] with
    def name: Text = t"formaction"
    def serialize(url: Url): Text = url.show
 
  given poster: GenericHtmlAttribute["poster", Url] with
    def name: Text = t"poster"
    def serialize(url: Url): Text = url.show

  given src: GenericHtmlAttribute["src", Url] with
    def name: Text = t"src"
    def serialize(url: Url): Text = url.show
  
  given href: GenericHtmlAttribute["href", Url] with
    def name: Text = t"href"
    def serialize(url: Url): Text = url.show
  
  given manifest: GenericHtmlAttribute["manifest", Url] with
    def name: Text = t"manifest"
    def serialize(url: Url): Text = url.show

  def parse(value: Text)(using Raises[UrlError], Raises[HostnameError]): Url =
    import UrlError.Expectation.*

    safely(value.where(_ == ':')) match
      case Unset =>
        raise(UrlError(value, value.length, Colon))(Url(Scheme.Https, Unset, t""))
      
      case colon: Int =>
        val scheme = Scheme(value.take(colon))
        
        val (pathStart, auth) =
          if value.slice(colon + 1, colon + 3) == t"//" then
            val authEnd = safely(value.where(_ == '/', colon + 3)).or(value.length)
            authEnd -> Authority.parse(value.slice(colon + 3, authEnd))
          else
            (colon + 1) -> Unset
        
        safely(value.where(_ == '?', pathStart)) match
          case Unset => safely(value.where(_ == '#', pathStart)) match
            case Unset     => Url(scheme, auth, value.drop(pathStart), Unset, Unset)
            case hash: Int => Url(scheme, auth, value.slice(pathStart, hash), Unset, value.drop(hash + 1))

          case qmark: Int =>
            safely(value.where(_ == '#', qmark + 1)) match
              case Unset     => Url(scheme, auth, value.slice(pathStart, qmark), value.drop(qmark + 1), Unset)
              case hash: Int => Url(scheme, auth, value.slice(pathStart, qmark), value.slice(qmark + 1, hash),
                                    value.drop(hash + 1))

object Authority:
  given Show[Authority] = auth =>
    t"${auth.userInfo.fm(t"")(_+t"@")}${auth.host}${auth.port.mm(_.show).fm(t"")(t":"+_)}"

  def parse(value: Text)(using Raises[UrlError]): Authority raises HostnameError =
    import UrlError.Expectation.*
    
    safely(value.where(_ == '@')) match
      case Unset => safely(value.where(_ == ':')) match
        case Unset =>
          Authority(Hostname.parse(value))
        
        case colon: Int =>
          safely(value.drop(colon + 1).s.toInt).match
            case port: Int if port >= 0 && port <= 65535 => port
            case port: Int                               => raise(UrlError(value, colon + 1, PortRange))(0)
            case Unset                                   => raise(UrlError(value, colon + 1, Number))(0)
          .pipe(Authority(Hostname.parse(value.take(colon)), Unset, _))
      
      case arobase: Int => safely(value.where(_ == ':', arobase + 1)) match
        case Unset =>
          Authority(Hostname.parse(value.drop(arobase + 1)), value.take(arobase))

        case colon: Int =>
          safely(value.drop(colon + 1).s.toInt).match
            case port: Int if port >= 0 && port <= 65535 => port
            case port: Int                               => raise(UrlError(value, colon + 1, PortRange))(0)
            case Unset                                   => raise(UrlError(value, colon + 1, Number))(0)
          .pipe(Authority(Hostname.parse(value.slice(arobase + 1, colon)), value.take(arobase), _))

case class Authority(host: Hostname, userInfo: Maybe[Text] = Unset, port: Maybe[Int] = Unset)

object Weblink:
  given Followable[Weblink, "", "..", "."] with
    def separators: Set[Char] = Set('/')
    def descent(weblink: Weblink): List[PathName[""]] = weblink.descent
    def separator(weblink: Weblink): Text = t"/"
    def ascent(weblink: Weblink): Int = weblink.ascent
    
  given PathCreator[Weblink, "", Int] with
    def path(ascent: Int, descent: List[PathName[""]]): Weblink = Weblink(ascent, descent)

case class Weblink(ascent: Int, descent: List[PathName[""]])

case class Url
    (scheme: Scheme, authority: Maybe[Authority], pathText: Text, query: Maybe[Text] = Unset,
        fragment: Maybe[Text] = Unset):
  
  lazy val path: List[PathName[""]] =
    // FIXME: This needs to be handled better
    import errorHandlers.throwUnsafely
    pathText.drop(1).cut(t"/").reverse.map(_.urlDecode).map(PathName(_))

object UrlError:
  enum Expectation:
    case Colon, More, LowerCaseLetter, PortRange, Number

  object Expectation:
    given MessageShow[Expectation] =
      case Colon           => msg"a colon"
      case More            => msg"more characters"
      case LowerCaseLetter => msg"a lowercase letter"
      case PortRange       => msg"a port range"
      case Number          => msg"a number"

enum LocalPart:
  case Quoted(text: Text)
  case Unquoted(text: Text)

object EmailAddress:
  def parse(text: Text): EmailAddress raises EmailAddressError =
    val buffer: StringBuilder = StringBuilder()
    if text.empty then abort(EmailAddressError())
    
    def quoted(index: Int, escape: Boolean): (LocalPart, Int) =
      safely(text(index)) match
        case '\"' =>
          if escape then
            buffer.append('\"')
            quoted(index + 1, false)
          else
            if safely(text(index + 1)) == '@'
            then (LocalPart.Quoted(buffer.toString.tt), index + 2)
            else abort(EmailAddressError())
        
        case '\\' =>
          if escape then buffer.append('\\')
          quoted(index + 1, !escape)
        
        case char: Char =>
          buffer.append(char)
          quoted(index + 1, false)

        case Unset =>
          raise(EmailAddressError())((LocalPart.Quoted(buffer.toString.tt), index))
    
    def unquoted(index: Int, dot: Boolean): (LocalPart, Int) =
      safely(text(index)) match
        case '@' =>
          if dot then raise(EmailAddressError())(())
          if buffer.length > 64 then raise(EmailAddressError())(())
          (LocalPart.Unquoted(buffer.toString.tt), index + 1)

        case '.'  =>
          if dot then raise(EmailAddressError())(())
          if index == 0 then raise(EmailAddressError())(())
          buffer.append('.')
          unquoted(index + 1, true)

        case char: Char =>
          if 'A' <= char <= 'Z' || 'a' <= char <= 'z' || char.isDigit || t"!#$$%&'*+-/=?^_`{|}~".contains(char)
          then buffer.append(char)
          else raise(EmailAddressError())(())
          unquoted(index + 1, false)

        case Unset =>
          raise(EmailAddressError())((LocalPart.Unquoted(buffer.toString.tt), index))
    
    val (localPart, index) =
      if text.starts(t"\"") then quoted(1, false) else unquoted(0, false)

    val domain =
      if text.length < index + 1 then abort(EmailAddressError())
      else if safely(text(index)) == '[' then
        try
          import errorHandlers.throwUnsafely
          if text.last != ']' then abort(EmailAddressError())
          val ipAddress = text.slice(index + 1, text.length - 1)
          if ipAddress.starts(t"IPv6:") then Ipv6.parse(ipAddress.drop(5)) else Ipv4.parse(ipAddress)
        catch case error: IpAddressError =>
          abort(EmailAddressError())
      else safely(Hostname.parse(text.drop(index))).or(abort(EmailAddressError()))

    EmailAddress(Unset, localPart, domain)

case class EmailAddress(displayName: Maybe[Text], localPart: LocalPart, domain: Hostname | Ipv4 | Ipv6)


