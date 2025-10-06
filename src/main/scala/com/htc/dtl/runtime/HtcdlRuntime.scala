package com.htc.dtl.runtime

import com.htc.dtl.model.{StateMachine, Transition}
import scala.collection.mutable

// Mock type for demonstration (would be provided by HTC simulator)
type Tick = Long

/**
 * Runtime support for HTCDL-generated actors.
 * These traits and classes provide the necessary infrastructure
 * for HTCDL models to work with the HTC simulator.
 */

/**
 * Marker trait for HTCDL-based state classes
 */
trait HtcdlState:
  def currentState: String
  def updateTick(tick: Tick): Unit

/**
 * Configuration for state machines generated from HTCDL models
 */
case class StateMachineConfig(
  initialState: String,
  states: Set[String],
  transitions: List[(String, String, String)] // (from, to, trigger)
)

object StateMachineConfig:
  def empty: StateMachineConfig = StateMachineConfig("", Set.empty, List.empty)

/**
 * State machine manager for HTCDL actors
 */
class StateMachineManager(config: StateMachineConfig):
  private val transitionMap = mutable.Map[String, List[(String, String)]]()
  
  // Build transition lookup table
  config.transitions.foreach { case (from, to, trigger) =>
    val existing = transitionMap.getOrElse(from, List.empty)
    transitionMap(from) = (to, trigger) :: existing
  }
  
  def initialize(initialState: String): Unit =
    if (!config.states.contains(initialState)) {
      throw new IllegalStateException(s"Initial state '$initialState' not found in state machine")
    }
  
  def getAvailableTransitions(currentState: String): List[(String, String, String)] =
    transitionMap.getOrElse(currentState, List.empty).map { case (to, trigger) =>
      (currentState, to, trigger)
    }
  
  def canTransition(from: String, to: String): Boolean =
    transitionMap.get(from).exists(_.exists(_._1 == to))
  
  def processTick(tick: Tick, state: HtcdlState): Unit =
    // Process any time-based state transitions
    // This can be extended based on model requirements
    ()
  
  def processTimeStep(tick: Tick, state: HtcdlState): Unit =
    // Process time-stepped state transitions
    // This can be extended based on model requirements
    ()

/**
 * Trait for command handling in HTCDL actors
 */
trait CommandHandler:
  /**
   * Register a command handler function
   */
  def registerCommand(commandName: String, handler: Any => Unit): Unit
  
  /**
   * Execute a command by name
   */
  def executeCommand(commandName: String, data: Any): Unit

/**
 * Trait for event emission in HTCDL actors
 */
trait EventEmitter:
  /**
   * Register an event emitter function
   */
  def registerEvent(eventName: String, emitter: () => Unit): Unit
  
  /**
   * Emit an event by name
   */
  def emitEvent(eventName: String, payload: Any = null): Unit

/**
 * Rules engine for HTCDL actors
 */
class RulesEngine:
  private val rules = mutable.Map[String, () => Boolean]()
  private val actions = mutable.Map[String, () => Unit]()
  
  def addRule(name: String, condition: () => Boolean, action: () => Unit): Unit =
    rules(name) = condition
    actions(name) = action
  
  def executeRules(): Unit =
    rules.foreach { case (name, condition) =>
      if (condition()) {
        actions.get(name).foreach(_())
      }
    }

/**
 * Telemetry manager for HTCDL actors
 */
class TelemetryManager:
  private val telemetryValues = mutable.Map[String, () => Any]()
  private val emissionSchedule = mutable.Map[String, (Long, Long)]() // (interval, lastEmission)
  
  def registerTelemetry(name: String, valueProvider: () => Any, interval: Long = 1000): Unit =
    telemetryValues(name) = valueProvider
    emissionSchedule(name) = (interval, 0L)
  
  def shouldEmit(name: String, currentTime: Long): Boolean =
    emissionSchedule.get(name) match
      case Some((interval, lastEmission)) => 
        currentTime - lastEmission >= interval
      case None => false
  
  def getValue(name: String): Option[Any] =
    telemetryValues.get(name).map(_())
  
  def updateEmissionTime(name: String, time: Long): Unit =
    emissionSchedule.get(name).foreach { case (interval, _) =>
      emissionSchedule(name) = (interval, time)
    }

/**
 * HTCDL model metadata
 */
case class ModelMetadata(
  id: String,
  version: String,
  displayName: String,
  description: String,
  capabilities: ModelCapabilities
)

case class ModelCapabilities(
  hasStateMachine: Boolean,
  hasRules: Boolean,
  hasTelemetry: Boolean,
  hasAI: Boolean,
  hasPhysics: Boolean,
  commandCount: Int,
  eventCount: Int,
  stateCount: Int
)

/**
 * Factory for creating HTCDL actors from model files
 */
object HtcdlActorFactory:
  
  /**
   * Create an actor instance from an HTCDL model file
   */
  def createFromModel(
    modelPath: String, 
    properties: Any  // Would be org.interscity.htc.core.entity.actor.properties.Properties
  ): Any =  // Would return org.interscity.htc.core.actor.BaseActor[_]
    // This would use reflection or a registry to create the appropriate actor
    // based on the model type
    println(s"Creating actor from model at $modelPath")
    "MockActorInstance"
  
  /**
   * Register an actor class for a specific model ID
   */
  private val actorRegistry = mutable.Map[String, Class[_]]()
  
  def registerActor(modelId: String, actorClass: Class[_]): Unit =
    actorRegistry(modelId) = actorClass
  
  def getActorClass(modelId: String): Option[Class[_]] =
    actorRegistry.get(modelId)

/**
 * Utility functions for HTCDL runtime
 */
object HtcdlUtils:
  
  /**
   * Convert DTMI to class name
   */
  def dtmiToClassName(dtmi: String): String =
    val parts = dtmi.split("[;:]")
    if (parts.length >= 2) {
      parts(parts.length - 2).split("\\.").last.capitalize + "Actor"
    } else {
      "UnknownActor"
    }
  
  /**
   * Validate actor state against HTCDL model
   */
  def validateState(state: HtcdlState, allowedStates: Set[String]): Boolean =
    allowedStates.contains(state.currentState)
  
  /**
   * Create a dependency injection container for HTCDL actors
   */
  def createDIContainer(): mutable.Map[String, Any] =
    mutable.Map(
      "rulesEngine" -> new RulesEngine(),
      "telemetryManager" -> new TelemetryManager(),
      "stateMachineManager" -> null // Will be set by specific actor
    )