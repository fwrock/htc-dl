package com.htc.dtl.codegen

import com.htc.dtl.model.*
import com.htc.dtl.parser.HtcModelParser
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

/**
 * Generator for HTC Actor classes based on HTCDL models.
 * This generator creates Scala actors that extend the existing BaseActor
 * and implement the behavior defined in HTCDL models.
 */
object HtcActorGenerator:

  /**
   * Generates a complete actor class from an HTCDL model.
   * 
   * @param model The HTCDL model
   * @param packageName The target package name for the generated actor
   * @param baseActorImport Optional custom import for BaseActor
   * @return Generated Scala code as string
   */
  def generateActor(
    model: HtcModel, 
    packageName: String = "org.interscity.htc.generated",
    baseActorImport: String = "org.interscity.htc.core.actor.BaseActor"
  ): String =
    val className = sanitizeClassName(model.displayName)
    val stateClassName = s"${className}State"
    
    s"""package $packageName

import $baseActorImport
import org.interscity.htc.core.entity.state.BaseState
import org.interscity.htc.core.entity.event.{ActorInteractionEvent, SpontaneousEvent}
import org.interscity.htc.core.entity.event.control.simulation.AdvanceToTick
import org.interscity.htc.core.types.Tick
import org.interscity.htc.core.entity.actor.properties.Properties
import org.interscity.htc.core.enumeration.TimePolicyEnum
import com.htc.dtl.runtime.{HtcdlState, StateMachineManager, CommandHandler, EventEmitter}
import com.typesafe.config.ConfigFactory
import scala.collection.mutable

/**
 * Generated actor class for ${model.displayName}
 * Based on HTCDL model: ${model.`@id`}
 * 
 * ${model.description}
 */
class $className(properties: Properties) 
    extends BaseActor[$stateClassName](properties) 
    with CommandHandler 
    with EventEmitter:

  // HTCDL Model metadata
  private val modelId = "${model.`@id`}"
  private val modelVersion = "${model.`@versionInfo`.map(_.version).getOrElse("1.0.0")}"
  
  // State machine manager
  private val stateMachine = new StateMachineManager(${generateStateMachineConfig(model.stateMachine)})
  
  // Command handlers registry
  private val commandHandlers = mutable.Map[String, Any => Unit]()
  
  // Event emitters registry  
  private val eventEmitters = mutable.Map[String, () => Unit]()

${generatePropertyAccessors(model.properties)}

${generateCommandHandlers(model.commands)}

${generateEventEmitters(model.events)}

${generateStateMachineLogic(model.stateMachine)}

${generateRulesEngine(model.rules)}

${generateTelemetryEmission(model.telemetry)}

  override def onStart(): Unit =
    super.onStart()
    initializeModel()
    logInfo(s"$className initialized with model ${model.`@id`} v${model.`@versionInfo`.map(_.version).getOrElse("unknown")}")

  private def initializeModel(): Unit =
    // Initialize state machine
    if (state == null) {
      state = $stateClassName()
      stateMachine.initialize(state.currentState)
    }
    
    // Register command handlers
${generateCommandRegistrations(model.commands)}
    
    // Register event emitters
${generateEventRegistrations(model.events)}
    
    // Start telemetry emission if configured
    startTelemetryEmission()
    
    logInfo(s"Model initialized - Current state: $${state.currentState}")

  override def actSpontaneous(event: SpontaneousEvent): Unit =
    try
      // Execute rules engine
      executeRules()
      
      // Handle state machine logic
      stateMachine.processTick(currentTick, state)
      
      // Execute model-specific spontaneous behavior
      actModelSpontaneous(event)
      
      // Emit telemetry
      emitTelemetry()
      
    catch
      case e: Exception =>
        logError(s"Error in spontaneous action: $${e.getMessage}", e)
    finally
      onFinishSpontaneous(getNextScheduledTick())

  override def actInteractWith(event: ActorInteractionEvent): Unit =
    try
      event.eventType match
${generateInteractionHandlers(model.commands)}
        case eventType => 
          logWarn(s"Unknown interaction event type: $$eventType")
          actModelInteraction(event)
    catch
      case e: Exception =>
        logError(s"Error handling interaction $$event: $${e.getMessage}", e)

  override def actAdvanceToTick(event: AdvanceToTick): Unit =
    try
      // Update current state for time-stepped simulation
      state.updateTick(event.targetTick)
      
      // Execute rules for this tick
      executeRules()
      
      // Process state machine for this tick
      stateMachine.processTimeStep(event.targetTick, state)
      
      // Execute model-specific time-stepped behavior
      actModelTimeStep(event)
      
      // Emit telemetry for this tick
      emitTelemetry()
      
    catch
      case e: Exception =>
        logError(s"Error in time-stepped execution for tick $${event.targetTick}: $${e.getMessage}", e)

  // Model-specific behavior hooks (to be overridden by users if needed)
  protected def actModelSpontaneous(event: SpontaneousEvent): Unit = ()
  protected def actModelInteraction(event: ActorInteractionEvent): Unit = ()
  protected def actModelTimeStep(event: AdvanceToTick): Unit = ()

${generateUtilityMethods(model)}

/**
 * Generated state class for ${model.displayName}
 */
case class $stateClassName(
${generateStateFields(model.properties)}
  currentState: String = "${model.stateMachine.map(_.initialState).getOrElse("Default")}",
  lastTelemetryTick: Tick = 0,
  scheduledEvents: mutable.Map[Tick, List[String]] = mutable.Map.empty
) extends BaseState with HtcdlState:

  override def getStartTick: Tick = 0
  override def isSetScheduleOnTimeManager: Boolean = true
  override def getReporterType = null

  def updateTick(tick: Tick): Unit =
    // Update state for new tick
    lastTelemetryTick = tick

  def transitionTo(newState: String): $stateClassName =
    copy(currentState = newState)

${generateStateHelperMethods(model.properties)}

object $className:
  /**
   * Factory method to create actor from HTCDL model file
   */
  def fromModel(modelPath: String, properties: Properties): $className =
    val model = HtcModelParser.parseFile(modelPath) match
      case Right(m) => m  
      case Left(error) => throw new RuntimeException(s"Failed to load HTCDL model: $$error")
    
    new $className(properties)
    
  /**
   * Get the HTCDL model ID for this actor type
   */
  def getModelId: String = "${model.`@id`}"
"""

  /**
   * Generates an actor from an HTCDL model file and saves it to disk.
   */
  def generateActorFile(
    modelPath: String,
    outputPath: String, 
    packageName: String = "org.interscity.htc.generated"
  ): Unit =
    val model = HtcModelParser.parseFile(modelPath) match
      case Right(m) => m
      case Left(error) => throw new RuntimeException(s"Failed to parse HTCDL model: $error")
    
    val actorCode = generateActor(model, packageName)
    val outputFile = Paths.get(outputPath)
    
    Files.createDirectories(outputFile.getParent)
    Files.writeString(outputFile, actorCode, StandardCharsets.UTF_8)

  // Helper methods for code generation
  
  private def sanitizeClassName(name: String): String =
    name.replaceAll("[^a-zA-Z0-9]", "").capitalize + "Actor"

  private def generateStateMachineConfig(stateMachine: Option[StateMachine]): String =
    stateMachine match
      case Some(sm) =>
        val states = sm.states.map(s => s"\"${s.name}\"").mkString(", ")
        val transitions = sm.transitions.map { t =>
          s"""("${t.from}", "${t.to}", "${t.trigger.command.orElse(t.trigger.event).getOrElse("condition")}")"""
        }.mkString(", ")
        s"""StateMachineConfig(
          |      initialState = "${sm.initialState}",
          |      states = Set($states),
          |      transitions = List($transitions)
          |    )""".stripMargin
      case None =>
        """StateMachineConfig.empty"""

  private def generatePropertyAccessors(properties: Option[List[Property]]): String =
    properties.map { props =>
      props.map { prop =>
        val propName = prop.name
        val propType = mapSchemaToScalaType(prop.schema)
        s"""  // Property: ${prop.name}
          |  def get${propName.capitalize}: $propType = state.$propName
          |  def set${propName.capitalize}(value: $propType): Unit = 
          |    state = state.copy($propName = value)
          |    logDebug(s"Property $propName updated to: $$value")""".stripMargin
      }.mkString("\n\n")
    }.getOrElse("")

  private def generateCommandHandlers(commands: Option[List[Command]]): String =
    commands.map { cmds =>
      cmds.map { cmd =>
        val cmdName = cmd.name
        val handlerName = s"handle${cmdName.capitalize}"
        s"""  // Command: ${cmd.name}
          |  def $handlerName(data: Any): Unit =
          |    logInfo(s"Executing command: $cmdName")
          |    try
          |      // TODO: Implement command logic for $cmdName
          |      ${generateCommandLogic(cmd)}
          |      
          |      // Emit completion events if configured
          |      ${generateCompletionEvents(cmd)}
          |      
          |    catch
          |      case e: Exception =>
          |        logError(s"Error executing command $cmdName: $${e.getMessage}", e)
          |        ${generateFailureEvents(cmd)}""".stripMargin
      }.mkString("\n\n")
    }.getOrElse("")

  private def generateEventEmitters(events: Option[List[Event]]): String =
    events.map { evts =>
      evts.map { event =>
        val eventName = event.name
        val emitterName = s"emit${eventName.capitalize}"
        s"""  // Event: ${event.name}
          |  def $emitterName(payload: Any = null): Unit =
          |    logInfo(s"Emitting event: $eventName")
          |    // TODO: Implement event emission logic
          |    ${generateEventEmissionLogic(event)}""".stripMargin
      }.mkString("\n\n")
    }.getOrElse("")

  private def generateStateMachineLogic(stateMachine: Option[StateMachine]): String =
    stateMachine.map { sm =>
      s"""  // State Machine Logic
        |  private def processStateTransitions(): Unit =
        |    val currentState = state.currentState
        |    stateMachine.getAvailableTransitions(currentState).foreach { transition =>
        |      if (canTransition(transition)) {
        |        executeTransition(transition)
        |      }
        |    }
        |
        |  private def canTransition(transition: (String, String, String)): Boolean =
        |    val (from, to, trigger) = transition
        |    // TODO: Implement transition conditions
        |    true // Default implementation
        |
        |  private def executeTransition(transition: (String, String, String)): Unit =
        |    val (from, to, trigger) = transition
        |    logInfo(s"State transition: $$from -> $$to (trigger: $$trigger)")
        |    state = state.transitionTo(to)
        |    // TODO: Execute transition actions""".stripMargin
    }.getOrElse("")

  private def generateRulesEngine(rules: Option[List[Rule]]): String =
    rules.map { ruleList =>
      s"""  // Rules Engine
        |  private def executeRules(): Unit =
        |${ruleList.map(generateRuleLogic).mkString("\n")}""".stripMargin
    }.getOrElse("  private def executeRules(): Unit = ()")

  private def generateRuleLogic(rule: Rule): String =
    s"""    // Rule: ${rule.name}
      |    if (evaluateCondition("${rule.condition}")) {
      |      ${rule.action.emitEvent.map(evt => s"emit${evt.capitalize}()").getOrElse("// No action defined")}
      |    }""".stripMargin

  private def generateTelemetryEmission(telemetry: Option[List[Telemetry]]): String =
    telemetry.map { telList =>
      s"""  // Telemetry Emission
        |  private def emitTelemetry(): Unit =
        |${telList.map(generateTelemetryLogic).mkString("\n")}
        |
        |  private def startTelemetryEmission(): Unit =
        |    // Configure periodic telemetry emission based on model
        |    ${telList.filter(_.emissionProfile.exists(_.`type` == EmissionType.Periodic)).map { tel =>
        s"    // Start periodic emission for ${tel.name}"
      }.mkString("\n")}""".stripMargin
    }.getOrElse("")

  private def generateTelemetryLogic(telemetry: Telemetry): String =
    s"""    // Telemetry: ${telemetry.name}
      |    report(getTelemetryValue("${telemetry.name}"), "${telemetry.name}")""".stripMargin

  private def generateCommandRegistrations(commands: Option[List[Command]]): String =
    commands.map { cmds =>
      cmds.map { cmd =>
        s"""    commandHandlers("${cmd.name}") = handle${cmd.name.capitalize}"""
      }.mkString("\n")
    }.getOrElse("")

  private def generateEventRegistrations(events: Option[List[Event]]): String =
    events.map { evts =>
      evts.map { event =>
        s"""    eventEmitters("${event.name}") = () => emit${event.name.capitalize}()"""
      }.mkString("\n")
    }.getOrElse("")

  private def generateInteractionHandlers(commands: Option[List[Command]]): String =
    commands.map { cmds =>
      cmds.map { cmd =>
        s"""        case "${cmd.name}" => handle${cmd.name.capitalize}(event.data)"""
      }.mkString("\n")
    }.getOrElse("")

  private def generateCommandLogic(command: Command): String =
    command.intent match
      case Some(IntentType.Control) => "// Control command implementation"
      case Some(IntentType.Query) => "// Query command implementation"  
      case Some(IntentType.Monitor) => "// Monitor command implementation"
      case None => "// Command implementation"

  private def generateCompletionEvents(command: Command): String =
    command.completionEvents.map { ce =>
      val success = ce.success.map(evt => s"emit${evt.capitalize}()").getOrElse("")
      if (success.nonEmpty) success else "// No completion events defined"
    }.getOrElse("")

  private def generateFailureEvents(command: Command): String =
    command.completionEvents.flatMap(_.failure).map { evt =>
      s"emit${evt.capitalize}()"
    }.getOrElse("// No failure events defined")

  private def generateEventEmissionLogic(event: Event): String =
    "// Event emission logic"

  private def generateStateFields(properties: Option[List[Property]]): String =
    properties.map { props =>
      props.map { prop =>
        val propType = mapSchemaToScalaType(prop.schema)
        val defaultValue = getDefaultValue(propType)
        s"  ${prop.name}: $propType = $defaultValue,"
      }.mkString("\n")
    }.getOrElse("")

  private def generateStateHelperMethods(properties: Option[List[Property]]): String =
    properties.map { props =>
      props.map { prop =>
        s"""  def update${prop.name.capitalize}(value: ${mapSchemaToScalaType(prop.schema)}): ${prop.name}State =
          |    copy(${prop.name} = value)""".stripMargin
      }.mkString("\n\n")
    }.getOrElse("")

  private def generateUtilityMethods(model: HtcModel): String =
    s"""  // Utility Methods
      |  private def getNextScheduledTick(): Option[Tick] =
      |    state.scheduledEvents.keys.filter(_ > currentTick).minOption
      |
      |  private def evaluateCondition(condition: String): Boolean =
      |    // TODO: Implement condition evaluation engine
      |    true // Default implementation
      |
      |  private def getTelemetryValue(telemetryName: String): Any =
      |    // TODO: Implement telemetry value extraction
      |    telemetryName match
      |      case _ => "mock_value"
      |
      |  override def getStatistics: Map[String, Any] =
      |    super.getStatistics ++ Map(
      |      "modelId" -> modelId,
      |      "modelVersion" -> modelVersion,
      |      "currentState" -> state.currentState,
      |      "availableCommands" -> commandHandlers.keys.mkString(","),
      |      "availableEvents" -> eventEmitters.keys.mkString(",")
      |    )""".stripMargin

  private def mapSchemaToScalaType(schema: io.circe.Json): String =
    schema.asString match
      case Some("string") => "String"
      case Some("double") => "Double"
      case Some("boolean") => "Boolean"
      case Some("long") => "Long"
      case Some("int") => "Int"
      case _ => "Any"

  private def getDefaultValue(scalaType: String): String =
    scalaType match
      case "String" => "\"\""
      case "Double" => "0.0"
      case "Boolean" => "false"
      case "Long" => "0L"
      case "Int" => "0"
      case _ => "null"