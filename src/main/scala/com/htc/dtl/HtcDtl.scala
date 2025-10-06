package com.htc.dtl

import com.htc.dtl.model.*
import com.htc.dtl.parser.*
import com.htc.dtl.validation.*
import java.nio.file.Path

/**
 * Main API for the HTC Digital Twin Language library.
 * This is the primary entry point for parsing and working with HTCDL models.
 */
object HtcDtl:
  
  /**
   * Parse an HTCDL model from a JSON string.
   * 
   * @param jsonString The JSON string containing the HTCDL model
   * @return Either a ParseError or the parsed and validated HtcModel
   */
  def parse(jsonString: String): ParseResult[HtcModel] =
    HtcModelParser.parseString(jsonString)
  
  /**
   * Parse an HTCDL model from a file.
   * 
   * @param filePath Path to the .htcdl.json file
   * @return Either a ParseError or the parsed and validated HtcModel
   */
  def parseFile(filePath: String): ParseResult[HtcModel] =
    HtcModelParser.parseFile(filePath)
  
  /**
   * Parse an HTCDL model from a Path.
   * 
   * @param path Path object pointing to the .htcdl.json file
   * @return Either a ParseError or the parsed and validated HtcModel
   */
  def parseFile(path: Path): ParseResult[HtcModel] =
    HtcModelParser.parsePath(path)
  
  /**
   * Validate an already parsed HtcModel.
   * 
   * @param model The model to validate
   * @return Either validation errors or the validated model
   */
  def validate(model: HtcModel): Either[List[ValidationError], HtcModel] =
    model.validate match
      case cats.data.Validated.Valid(validModel) => Right(validModel)
      case cats.data.Validated.Invalid(errors) => Left(errors.toList)
  
  /**
   * Serialize an HtcModel to pretty-printed JSON.
   * 
   * @param model The model to serialize
   * @return JSON string representation
   */
  def toJson(model: HtcModel): String =
    ModelSerializer.toPrettyJson(model)
  
  /**
   * Get statistics about a model.
   * 
   * @param model The model to analyze
   * @return Model statistics
   */
  def analyze(model: HtcModel): ModelStatistics =
    ModelAnalyzer.getModelStatistics(model)
  
  /**
   * Find unused elements in a model.
   * 
   * @param model The model to analyze
   * @return Information about unused elements
   */
  def findUnused(model: HtcModel): UnusedElements =
    ModelAnalyzer.findUnusedElements(model)

/**
 * Builder for creating HtcModel instances programmatically.
 */
class HtcModelBuilder(
  private val context: String = "dtmi:htc:context;1",
  private val id: String,
  private val displayName: String,
  private val description: String
):
  private var versionInfo: Option[VersionInfo] = None
  private var schemas: List[ObjectSchema] = List.empty
  private var properties: List[Property] = List.empty
  private var telemetry: List[Telemetry] = List.empty
  private var commands: List[Command] = List.empty
  private var events: List[Event] = List.empty
  private var relationships: List[Relationship] = List.empty
  private var stateMachine: Option[StateMachine] = None
  private var physics: Option[Physics] = None
  private var rules: List[Rule] = List.empty
  private var goals: List[Goal] = List.empty
  private var aiModels: List[AiModel] = List.empty

  def withVersion(version: String, changeLog: Option[String] = None): HtcModelBuilder =
    versionInfo = Some(VersionInfo(version, changeLog))
    this

  def addSchema(schema: ObjectSchema): HtcModelBuilder =
    schemas = schemas :+ schema
    this

  def addProperty(property: Property): HtcModelBuilder =
    properties = properties :+ property
    this

  def addTelemetry(telemetry: Telemetry): HtcModelBuilder =
    this.telemetry = this.telemetry :+ telemetry
    this

  def addCommand(command: Command): HtcModelBuilder =
    commands = commands :+ command
    this

  def addEvent(event: Event): HtcModelBuilder =
    events = events :+ event
    this

  def addRelationship(relationship: Relationship): HtcModelBuilder =
    relationships = relationships :+ relationship
    this

  def withStateMachine(sm: StateMachine): HtcModelBuilder =
    stateMachine = Some(sm)
    this

  def withPhysics(physics: Physics): HtcModelBuilder =
    this.physics = Some(physics)
    this

  def addRule(rule: Rule): HtcModelBuilder =
    rules = rules :+ rule
    this

  def addGoal(goal: Goal): HtcModelBuilder =
    goals = goals :+ goal
    this

  def addAiModel(aiModel: AiModel): HtcModelBuilder =
    aiModels = aiModels :+ aiModel
    this

  def build(): HtcModel =
    HtcModel(
      `@context` = context,
      `@id` = id,
      `@type` = "Interface",
      displayName = displayName,
      description = description,
      `@versionInfo` = versionInfo,
      schemas = if schemas.nonEmpty then Some(schemas) else None,
      properties = if properties.nonEmpty then Some(properties) else None,
      telemetry = if telemetry.nonEmpty then Some(telemetry) else None,
      commands = if commands.nonEmpty then Some(commands) else None,
      events = if events.nonEmpty then Some(events) else None,
      relationships = if relationships.nonEmpty then Some(relationships) else None,
      stateMachine = stateMachine,
      physics = physics,
      rules = if rules.nonEmpty then Some(rules) else None,
      goals = if goals.nonEmpty then Some(goals) else None,
      aiModels = if aiModels.nonEmpty then Some(aiModels) else None
    )

object HtcModelBuilder:
  def apply(id: String, displayName: String, description: String): HtcModelBuilder =
    new HtcModelBuilder(id = id, displayName = displayName, description = description)

/**
 * Utility functions for working with HTCDL models.
 */
object HtcUtils:
  
  /**
   * Create a simple property.
   */
  def property(name: String, schema: String, writable: Boolean = false, unit: Option[String] = None): Property =
    import io.circe.Json
    Property(name, Json.fromString(schema), writable, unit)
  
  /**
   * Create a simple telemetry.
   */
  def telemetry(name: String, schema: String, unit: Option[String] = None): Telemetry =
    import io.circe.Json
    Telemetry(name, Json.fromString(schema), unit)
  
  /**
   * Create a simple command.
   */
  def command(name: String, intent: IntentType = IntentType.Control, mode: ExecutionMode = ExecutionMode.Async): Command =
    Command(name, Some(intent), Some(mode))
  
  /**
   * Create a simple event.
   */
  def event(name: String): Event =
    Event(name)
  
  /**
   * Create a state machine state.
   */
  def state(name: String): State =
    State(name)
  
  /**
   * Create a transition with a command trigger.
   */
  def transitionOnCommand(from: String, to: String, command: String, emitEvent: Option[String] = None): Transition =
    Transition(
      from = from,
      to = to,
      trigger = Trigger(command = Some(command)),
      action = emitEvent.map(evt => Action(emitEvent = Some(evt)))
    )
  
  /**
   * Create a transition with an event trigger.
   */
  def transitionOnEvent(from: String, to: String, event: String, updateProperty: Option[String] = None, value: Option[String] = None): Transition =
    import io.circe.Json
    Transition(
      from = from,
      to = to,
      trigger = Trigger(event = Some(event)),
      action = updateProperty.map(prop => Action(updateProperty = Some(prop), value = value.map(Json.fromString)))
    )
  
  /**
   * Create a rule.
   */
  def rule(name: String, condition: String, emitEvent: String): Rule =
    Rule(name, condition, Action(emitEvent = Some(emitEvent)))
  
  /**
   * Create a goal.
   */
  def goal(name: String, priority: Double = 1.0): Goal =
    Goal(name, priority)
  
  /**
   * Create an AI model.
   */
  def aiModel(name: String, purpose: String, modelUri: String): AiModel =
    AiModel(name, purpose, modelUri)

// Exception types for better error handling
class HtcParseException(message: String, cause: Throwable = null) extends Exception(message, cause)
class HtcValidationException(val errors: List[ValidationError]) extends Exception(ErrorFormatter.formatParseError(ParseError.ValidationErrors(errors)))

// Extension methods for better usability
extension (parseResult: ParseResult[HtcModel])
  def toTry: scala.util.Try[HtcModel] = parseResult match
    case Right(model) => scala.util.Success(model)
    case Left(error) => scala.util.Failure(HtcParseException(ErrorFormatter.formatParseError(error)))
  
  def toOption: Option[HtcModel] = parseResult.toOption