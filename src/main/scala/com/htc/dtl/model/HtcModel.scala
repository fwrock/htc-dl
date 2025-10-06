package com.htc.dtl.model

import io.circe.*
import io.circe.generic.semiauto.*

// Core types and enums
enum IntentType:
  case Control, Query, Monitor

enum ExecutionMode:
  case Sync, Async

enum EmissionType:
  case Periodic, OnChange, OnDemand

// Version info
case class VersionInfo(
  version: String,
  changeLog: Option[String] = None
)

// Schema definitions
case class SchemaField(
  name: String,
  schema: Json,
  unit: Option[String] = None
)

case class ObjectSchema(
  `@id`: String,
  `@type`: String,
  fields: List[SchemaField]
)

// Emission profiles for telemetry
case class EmissionProfile(
  `type`: EmissionType,
  rate: Option[Double] = None,
  unit: Option[String] = None,
  tolerance: Option[Double] = None
)

// Properties (state)
case class Property(
  name: String,
  schema: Json,
  writable: Boolean = false,
  unit: Option[String] = None,
  semanticId: Option[String] = None
)

// Telemetry (data streams)
case class Telemetry(
  name: String,
  schema: Json,
  unit: Option[String] = None,
  emissionProfile: Option[EmissionProfile] = None
)

// Command completion events
case class CompletionEvents(
  success: Option[String] = None,
  failure: Option[String] = None
)

// Commands (actions)
case class Command(
  name: String,
  intent: Option[IntentType] = None,
  executionMode: Option[ExecutionMode] = None,
  requestSchema: Option[Json] = None,
  responseSchema: Option[Json] = None,
  completionEvents: Option[CompletionEvents] = None
)

// Events (notifications)
case class Event(
  name: String,
  payloadSchema: Option[Json] = None
)

// Relationships
case class Relationship(
  `@type`: String,
  name: String,
  target: String
)

// State machine components
case class State(
  name: String
)

case class Trigger(
  command: Option[String] = None,
  event: Option[String] = None,
  condition: Option[String] = None
)

case class Action(
  emitEvent: Option[String] = None,
  updateProperty: Option[String] = None,
  value: Option[Json] = None
)

case class Transition(
  from: String,
  to: String,
  trigger: Trigger,
  action: Option[Action] = None
)

case class StateMachine(
  initialState: String,
  states: List[State],
  transitions: List[Transition]
)

// Physics
case class Dimensions(
  length: Double,
  width: Double,
  height: Double,
  unit: String
)

case class Mass(
  value: Double,
  unit: String
)

case class Physics(
  mass: Option[Mass] = None,
  dimensions: Option[Dimensions] = None
)

// Rules
case class Rule(
  name: String,
  condition: String,
  action: Action
)

// AI Models and Goals
case class Goal(
  name: String,
  priority: Double
)

case class AiModel(
  name: String,
  purpose: String,
  modelUri: String,
  inputSchema: Option[String] = None,
  outputSchema: Option[String] = None
)

// Main HTC Model
case class HtcModel(
  `@context`: String,
  `@id`: String,
  `@type`: String,
  displayName: String,
  description: String,
  `@versionInfo`: Option[VersionInfo] = None,
  schemas: Option[List[ObjectSchema]] = None,
  properties: Option[List[Property]] = None,
  telemetry: Option[List[Telemetry]] = None,
  commands: Option[List[Command]] = None,
  events: Option[List[Event]] = None,
  relationships: Option[List[Relationship]] = None,
  stateMachine: Option[StateMachine] = None,
  physics: Option[Physics] = None,
  rules: Option[List[Rule]] = None,
  goals: Option[List[Goal]] = None,
  aiModels: Option[List[AiModel]] = None
)

// Circe codecs
object HtcModel:
  given Decoder[IntentType] = Decoder.decodeString.emap {
    case "control" => Right(IntentType.Control)
    case "query" => Right(IntentType.Query)
    case "monitor" => Right(IntentType.Monitor)
    case other => Left(s"Invalid intent type: $other")
  }

  given Encoder[IntentType] = Encoder.encodeString.contramap {
    case IntentType.Control => "control"
    case IntentType.Query => "query"
    case IntentType.Monitor => "monitor"
  }

  given Decoder[ExecutionMode] = Decoder.decodeString.emap {
    case "sync" => Right(ExecutionMode.Sync)
    case "async" => Right(ExecutionMode.Async)
    case other => Left(s"Invalid execution mode: $other")
  }

  given Encoder[ExecutionMode] = Encoder.encodeString.contramap {
    case ExecutionMode.Sync => "sync"
    case ExecutionMode.Async => "async"
  }

  given Decoder[EmissionType] = Decoder.decodeString.emap {
    case "periodic" => Right(EmissionType.Periodic)
    case "onChange" => Right(EmissionType.OnChange)
    case "onDemand" => Right(EmissionType.OnDemand)
    case other => Left(s"Invalid emission type: $other")
  }

  given Encoder[EmissionType] = Encoder.encodeString.contramap {
    case EmissionType.Periodic => "periodic"
    case EmissionType.OnChange => "onChange"
    case EmissionType.OnDemand => "onDemand"
  }

  given Decoder[VersionInfo] = deriveDecoder[VersionInfo]
  given Encoder[VersionInfo] = deriveEncoder[VersionInfo]
  
  given Decoder[SchemaField] = deriveDecoder[SchemaField]
  given Encoder[SchemaField] = deriveEncoder[SchemaField]
  
  given Decoder[ObjectSchema] = deriveDecoder[ObjectSchema]
  given Encoder[ObjectSchema] = deriveEncoder[ObjectSchema]
  
  given Decoder[EmissionProfile] = deriveDecoder[EmissionProfile]
  given Encoder[EmissionProfile] = deriveEncoder[EmissionProfile]
  
  given Decoder[Property] = deriveDecoder[Property]
  given Encoder[Property] = deriveEncoder[Property]
  
  given Decoder[Telemetry] = deriveDecoder[Telemetry]
  given Encoder[Telemetry] = deriveEncoder[Telemetry]
  
  given Decoder[CompletionEvents] = deriveDecoder[CompletionEvents]
  given Encoder[CompletionEvents] = deriveEncoder[CompletionEvents]
  
  given Decoder[Command] = deriveDecoder[Command]
  given Encoder[Command] = deriveEncoder[Command]
  
  given Decoder[Event] = deriveDecoder[Event]
  given Encoder[Event] = deriveEncoder[Event]
  
  given Decoder[Relationship] = deriveDecoder[Relationship]
  given Encoder[Relationship] = deriveEncoder[Relationship]
  
  given Decoder[State] = deriveDecoder[State]
  given Encoder[State] = deriveEncoder[State]
  
  given Decoder[Trigger] = deriveDecoder[Trigger]
  given Encoder[Trigger] = deriveEncoder[Trigger]
  
  given Decoder[Action] = deriveDecoder[Action]
  given Encoder[Action] = deriveEncoder[Action]
  
  given Decoder[Transition] = deriveDecoder[Transition]
  given Encoder[Transition] = deriveEncoder[Transition]
  
  given Decoder[StateMachine] = deriveDecoder[StateMachine]
  given Encoder[StateMachine] = deriveEncoder[StateMachine]
  
  given Decoder[Dimensions] = deriveDecoder[Dimensions]
  given Encoder[Dimensions] = deriveEncoder[Dimensions]
  
  given Decoder[Mass] = deriveDecoder[Mass]
  given Encoder[Mass] = deriveEncoder[Mass]
  
  given Decoder[Physics] = deriveDecoder[Physics]
  given Encoder[Physics] = deriveEncoder[Physics]
  
  given Decoder[Rule] = deriveDecoder[Rule]
  given Encoder[Rule] = deriveEncoder[Rule]
  
  given Decoder[Goal] = deriveDecoder[Goal]
  given Encoder[Goal] = deriveEncoder[Goal]
  
  given Decoder[AiModel] = deriveDecoder[AiModel]
  given Encoder[AiModel] = deriveEncoder[AiModel]
  
  given Decoder[HtcModel] = deriveDecoder[HtcModel]
  given Encoder[HtcModel] = deriveEncoder[HtcModel]