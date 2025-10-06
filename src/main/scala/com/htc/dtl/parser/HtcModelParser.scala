package com.htc.dtl.parser

import com.htc.dtl.model.*
import com.htc.dtl.validation.*
import io.circe.*
import io.circe.parser.*
import cats.data.ValidatedNel
import cats.implicits.*
import cats.effect.IO
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import scala.util.{Try, Success, Failure}

// Parse error types
enum ParseError:
  case FileNotFound(path: String)
  case InvalidJson(message: String)
  case DecodingError(message: String)
  case ValidationErrors(errors: List[ValidationError])
  case IoError(message: String)

type ParseResult[T] = Either[ParseError, T]

// Main parser class
class HtcModelParser:
  
  def parseFromString(jsonString: String): ParseResult[HtcModel] =
    for
      json <- parse(jsonString).left.map(err => ParseError.InvalidJson(err.getMessage))
      model <- json.as[HtcModel].left.map(err => ParseError.DecodingError(err.getMessage))
      validatedModel <- validateModel(model)
    yield validatedModel

  def parseFromFile(filePath: String): ParseResult[HtcModel] =
    readFile(filePath).flatMap(parseFromString)

  def parseFromPath(path: Path): ParseResult[HtcModel] =
    parseFromFile(path.toString)

  private def readFile(filePath: String): ParseResult[String] =
    Try {
      val path = Paths.get(filePath)
      if !Files.exists(path) then
        throw new java.io.FileNotFoundException(s"File not found: $filePath")
      Files.readString(path, StandardCharsets.UTF_8)
    } match
      case Success(content) => Right(content)
      case Failure(_: java.io.FileNotFoundException) => Left(ParseError.FileNotFound(filePath))
      case Failure(ex) => Left(ParseError.IoError(ex.getMessage))

  private def validateModel(model: HtcModel): ParseResult[HtcModel] =
    model.validate match
      case cats.data.Validated.Valid(validModel) => Right(validModel)
      case cats.data.Validated.Invalid(errors) => 
        Left(ParseError.ValidationErrors(errors.toList))

// Companion object with utility methods
object HtcModelParser:
  
  def apply(): HtcModelParser = new HtcModelParser()
  
  // Convenience methods
  def parseString(jsonString: String): ParseResult[HtcModel] =
    HtcModelParser().parseFromString(jsonString)
    
  def parseFile(filePath: String): ParseResult[HtcModel] =
    HtcModelParser().parseFromFile(filePath)
    
  def parsePath(path: Path): ParseResult[HtcModel] =
    HtcModelParser().parseFromPath(path)

// Error formatting utilities
object ErrorFormatter:
  
  def formatParseError(error: ParseError): String = error match
    case ParseError.FileNotFound(path) =>
      s"File not found: $path"
      
    case ParseError.InvalidJson(message) =>
      s"Invalid JSON format: $message"
      
    case ParseError.DecodingError(message) =>
      s"JSON decoding error: $message"
      
    case ParseError.ValidationErrors(errors) =>
      val errorMessages = errors.map(formatValidationError).mkString("\n  - ")
      s"Model validation failed:\n  - $errorMessages"
      
    case ParseError.IoError(message) =>
      s"I/O error: $message"

  def formatValidationError(error: ValidationError): String = error match
    case ValidationError.InvalidReference(elementType, name, referencedType, referencedName) =>
      s"$elementType '$name' references non-existent $referencedType '$referencedName'"
      
    case ValidationError.DuplicateName(elementType, name) =>
      s"Duplicate $elementType name: '$name'"
      
    case ValidationError.InvalidStateTransition(from, to, reason) =>
      s"Invalid state transition from '$from' to '$to': $reason"
      
    case ValidationError.MissingRequiredField(elementType, fieldName) =>
      s"$elementType is missing required field: '$fieldName'"
      
    case ValidationError.InvalidFieldValue(elementType, fieldName, value, reason) =>
      s"$elementType has invalid value for field '$fieldName' = '$value': $reason"
      
    case ValidationError.InvalidContext(expected, actual) =>
      s"Invalid context: expected '$expected', got '$actual'"
      
    case ValidationError.InvalidDtmi(dtmi, reason) =>
      s"Invalid DTMI '$dtmi': $reason"
      
    case ValidationError.CircularReference(path) =>
      s"Circular reference detected: ${path.mkString(" -> ")}"

// Result extensions for better usability
extension [T](result: ParseResult[T])
  def getOrThrow: T = result match
    case Right(value) => value
    case Left(error) => throw new RuntimeException(ErrorFormatter.formatParseError(error))
    
  def printErrors(): Unit = result match
    case Left(error) => println(ErrorFormatter.formatParseError(error))
    case Right(_) => ()

// Model serialization utilities
object ModelSerializer:
  
  def toJson(model: HtcModel): String =
    import HtcModel.given
    import io.circe.syntax.*
    model.asJson.spaces2
    
  def toPrettyJson(model: HtcModel): String =
    import HtcModel.given
    import io.circe.syntax.*
    model.asJson.spaces4
    
  def writeToFile(model: HtcModel, filePath: String): Try[Unit] =
    Try {
      val content = toPrettyJson(model)
      Files.writeString(Paths.get(filePath), content, StandardCharsets.UTF_8)
    }

// Schema utilities
object SchemaUtils:
  
  def extractSchemaIds(model: HtcModel): List[String] =
    model.schemas.getOrElse(List.empty).map(_.`@id`)
    
  def findSchema(model: HtcModel, schemaId: String): Option[ObjectSchema] =
    model.schemas.getOrElse(List.empty).find(_.`@id` == schemaId)
    
  def getAllReferencedSchemas(model: HtcModel): Set[String] =
    val fromTelemetry = model.telemetry.getOrElse(List.empty).flatMap(tel => extractSchemaReferences(tel.schema))
    val fromCommands = model.commands.getOrElse(List.empty).flatMap(cmd => 
      extractSchemaReferences(cmd.requestSchema) ++ extractSchemaReferences(cmd.responseSchema)
    )
    val fromEvents = model.events.getOrElse(List.empty).flatMap(evt =>
      extractSchemaReferences(evt.payloadSchema)
    )
    
    (fromTelemetry ++ fromCommands ++ fromEvents).toSet
    
  private def extractSchemaReferences(jsonOpt: Option[Json]): List[String] =
    jsonOpt.toList.flatMap(extractSchemaReferences)
    
  private def extractSchemaReferences(json: Json): List[String] =
    // Simple schema reference extraction - can be enhanced
    json.asString.filter(_.startsWith("dtmi:")).toList

// Model analysis utilities  
object ModelAnalyzer:
  
  def getModelStatistics(model: HtcModel): ModelStatistics =
    ModelStatistics(
      propertyCount = model.properties.map(_.size).getOrElse(0),
      telemetryCount = model.telemetry.map(_.size).getOrElse(0),
      commandCount = model.commands.map(_.size).getOrElse(0),
      eventCount = model.events.map(_.size).getOrElse(0),
      relationshipCount = model.relationships.map(_.size).getOrElse(0),
      stateCount = model.stateMachine.map(_.states.size).getOrElse(0),
      transitionCount = model.stateMachine.map(_.transitions.size).getOrElse(0),
      ruleCount = model.rules.map(_.size).getOrElse(0),
      goalCount = model.goals.map(_.size).getOrElse(0),
      aiModelCount = model.aiModels.map(_.size).getOrElse(0),
      hasStateMachine = model.stateMachine.isDefined,
      hasPhysics = model.physics.isDefined
    )
    
  def findUnusedElements(model: HtcModel): UnusedElements =
    val allEventNames = model.events.getOrElse(List.empty).map(_.name).toSet
    val referencedEvents = extractReferencedEvents(model)
    val unusedEvents = allEventNames -- referencedEvents
    
    UnusedElements(
      unusedEvents = unusedEvents.toList,
      unusedSchemas = List.empty // Can be implemented similarly
    )
    
  private def extractReferencedEvents(model: HtcModel): Set[String] =
    val fromCommands = model.commands.getOrElse(List.empty).flatMap { cmd =>
      cmd.completionEvents.map { ce =>
        List(ce.success, ce.failure).flatten
      }.getOrElse(List.empty)
    }
    
    val fromTransitions = model.stateMachine.map { sm =>
      sm.transitions.flatMap { t =>
        List(t.trigger.event, t.action.flatMap(_.emitEvent)).flatten
      }
    }.getOrElse(List.empty)
    
    (fromCommands ++ fromTransitions).toSet

case class ModelStatistics(
  propertyCount: Int,
  telemetryCount: Int,
  commandCount: Int,
  eventCount: Int,
  relationshipCount: Int,
  stateCount: Int,
  transitionCount: Int,
  ruleCount: Int,
  goalCount: Int,
  aiModelCount: Int,
  hasStateMachine: Boolean,
  hasPhysics: Boolean
)

case class UnusedElements(
  unusedEvents: List[String],
  unusedSchemas: List[String]
)