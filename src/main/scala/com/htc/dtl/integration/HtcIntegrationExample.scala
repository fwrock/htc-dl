package com.htc.dtl.integration

import com.htc.dtl.{HtcDtl, HtcModelBuilder, HtcUtils}
import com.htc.dtl.model.*
import com.htc.dtl.codegen.HtcActorGenerator
import com.htc.dtl.runtime.*
import scala.collection.mutable

// Mock HTC simulator classes for demonstration
case class MockProperties(
  entityId: String,
  resourceId: String,
  actorType: String,
  data: Any = null,
  dependencies: mutable.Map[String, Any] = mutable.Map.empty,
  reporters: mutable.Map[String, Any] = mutable.Map.empty,
  timeManager: Any = null,
  creatorManager: Any = null,
  timePolicy: Option[String] = None
)

/**
 * Example demonstrating how to integrate HTCDL with the HTC simulator.
 * This shows the complete workflow from model definition to actor generation.
 */
object HtcIntegrationExample:

  /**
   * Example 1: Generate actor from existing HTCDL model file
   */
  def generateActorFromFile(): Unit =
    println("=== Generating Actor from HTCDL File ===")
    
    // 1. Parse the HTCDL model
    val modelPath = "src/main/resources/examples/car-model.htcdl.json"
    val model = HtcDtl.parseFile(modelPath) match
      case Right(m) => m
      case Left(error) => 
        println(s"Failed to parse model: $error")
        return
    
    // 2. Generate actor code
    val actorCode = HtcActorGenerator.generateActor(
      model, 
      packageName = "org.interscity.htc.examples.generated"
    )
    
    // 3. Save generated actor to file
    val outputPath = "target/generated-sources/IntelligentSimulatedCarActor.scala"
    HtcActorGenerator.generateActorFile(modelPath, outputPath)
    
    println(s"✅ Actor generated and saved to: $outputPath")
    println(s"📊 Model statistics:")
    val stats = HtcDtl.analyze(model)
    println(s"   - Commands: ${stats.commandCount}")
    println(s"   - Events: ${stats.eventCount}")
    println(s"   - States: ${stats.stateCount}")

  /**
   * Example 2: Create model programmatically and generate actor
   */
  def createModelAndGenerateActor(): Unit =
    println("\n=== Creating Model and Generating Actor ===")
    
    // 1. Create a smart thermostat model programmatically
    val thermostatModel = HtcModelBuilder("dtmi:htc:hvac:thermostat;1", "Smart Thermostat", "An intelligent thermostat for HVAC control")
      .withVersion("1.0.0", Some("Initial smart thermostat model"))
      
      // Properties (state)
      .addProperty(HtcUtils.property("currentTemperature", "double", unit = Some("celsius")))
      .addProperty(HtcUtils.property("targetTemperature", "double", unit = Some("celsius")))
      .addProperty(HtcUtils.property("isHeating", "boolean"))
      .addProperty(HtcUtils.property("isCooling", "boolean"))
      .addProperty(HtcUtils.property("operationMode", "string")) // heating, cooling, auto, off
      
      // Telemetry
      .addTelemetry(Telemetry(
        name = "temperatureReading",
        schema = io.circe.Json.fromString("double"),
        unit = Some("celsius"),
        emissionProfile = Some(EmissionProfile(EmissionType.Periodic, Some(5.0), Some("perSecond")))
      ))
      .addTelemetry(Telemetry(
        name = "hvacStatus",
        schema = io.circe.Json.obj(
          "@type" -> io.circe.Json.fromString("Object"),
          "fields" -> io.circe.Json.arr(
            io.circe.Json.obj("name" -> io.circe.Json.fromString("heating"), "schema" -> io.circe.Json.fromString("boolean")),
            io.circe.Json.obj("name" -> io.circe.Json.fromString("cooling"), "schema" -> io.circe.Json.fromString("boolean")),
            io.circe.Json.obj("name" -> io.circe.Json.fromString("mode"), "schema" -> io.circe.Json.fromString("string"))
          )
        ),
        emissionProfile = Some(EmissionProfile(EmissionType.OnChange))
      ))
      
      // Commands
      .addCommand(Command(
        name = "setTargetTemperature",
        intent = Some(IntentType.Control),
        executionMode = Some(ExecutionMode.Sync),
        requestSchema = Some(io.circe.Json.obj(
          "name" -> io.circe.Json.fromString("setTempParams"),
          "@type" -> io.circe.Json.fromString("Object"),
          "fields" -> io.circe.Json.arr(
            io.circe.Json.obj("name" -> io.circe.Json.fromString("temperature"), "schema" -> io.circe.Json.fromString("double"), "unit" -> io.circe.Json.fromString("celsius"))
          )
        ))
      ))
      .addCommand(HtcUtils.command("setMode", IntentType.Control, ExecutionMode.Sync))
      .addCommand(HtcUtils.command("getStatus", IntentType.Query, ExecutionMode.Sync))
      
      // Events
      .addEvent(HtcUtils.event("targetReached"))
      .addEvent(HtcUtils.event("heatingStarted"))
      .addEvent(HtcUtils.event("coolingStarted"))
      .addEvent(HtcUtils.event("systemIdle"))
      .addEvent(HtcUtils.event("temperatureAlert"))
      
      // State Machine
      .withStateMachine(StateMachine(
        initialState = "Idle",
        states = List(
          HtcUtils.state("Idle"),
          HtcUtils.state("Heating"),
          HtcUtils.state("Cooling"),
          HtcUtils.state("Maintaining")
        ),
        transitions = List(
          HtcUtils.transitionOnCommand("Idle", "Heating", "setTargetTemperature"),
          HtcUtils.transitionOnEvent("Heating", "Maintaining", "targetReached"),
          HtcUtils.transitionOnCommand("Idle", "Cooling", "setTargetTemperature"),
          HtcUtils.transitionOnEvent("Cooling", "Maintaining", "targetReached"),
          HtcUtils.transitionOnCommand("Maintaining", "Idle", "setMode"),
          HtcUtils.transitionOnCommand("Heating", "Idle", "setMode"),
          HtcUtils.transitionOnCommand("Cooling", "Idle", "setMode")
        )
      ))
      
      // Rules
      .addRule(Rule(
        name = "StartHeatingRule",
        condition = "properties.currentTemperature < properties.targetTemperature - 1.0 && properties.operationMode == 'heating'",
        action = Action(emitEvent = Some("heatingStarted"))
      ))
      .addRule(Rule(
        name = "StartCoolingRule", 
        condition = "properties.currentTemperature > properties.targetTemperature + 1.0 && properties.operationMode == 'cooling'",
        action = Action(emitEvent = Some("coolingStarted"))
      ))
      .addRule(Rule(
        name = "TargetReachedRule",
        condition = "Math.abs(properties.currentTemperature - properties.targetTemperature) <= 0.5",
        action = Action(emitEvent = Some("targetReached"))
      ))
      .addRule(Rule(
        name = "TemperatureAlertRule",
        condition = "properties.currentTemperature < 10.0 || properties.currentTemperature > 35.0",
        action = Action(emitEvent = Some("temperatureAlert"))
      ))
      
      .build()

    // 2. Validate the model
    HtcDtl.validate(thermostatModel) match
      case Right(_) => println("✅ Thermostat model is valid!")
      case Left(errors) =>
        println("❌ Model validation failed:")
        errors.foreach(println)
        return

    // 3. Generate actor code
    val actorCode = HtcActorGenerator.generateActor(
      thermostatModel,
      packageName = "org.interscity.htc.examples.generated"
    )
    
    // 4. Save the model and actor
    import java.io.PrintWriter
    import java.io.File
    import io.circe.syntax.*
    
    val modelJson = thermostatModel.asJson
    val modelFile = new File("target/generated-models/thermostat-model.htcdl.json")
    modelFile.getParentFile.mkdirs()
    val writer = new PrintWriter(modelFile)
    writer.write(modelJson.spaces2)
    writer.close()
    
    val actorOutputPath = "target/generated-sources/SmartThermostatActor.scala"
    HtcActorGenerator.generateActorFile("target/generated-models/thermostat-model.htcdl.json", actorOutputPath)
    
    println(s"✅ Thermostat model and actor generated!")
    println(s"📄 Model saved to: target/generated-models/thermostat-model.htcdl.json")
    println(s"🎭 Actor saved to: $actorOutputPath")

  /**
   * Example 3: Simulate integration with HTC simulator
   */
  def simulateHtcIntegration(): Unit =
    println("\n=== Simulating HTC Integration ===")
    
    // Create mock properties (in real scenario, these come from HTC simulator)
    val properties = createMockProperties()
    
    // Show how the generated actor would be used
    println("🔄 Simulation steps:")
    println("1. HTC Simulator loads HTCDL model")
    println("2. Actor class is generated (or loaded from cache)")
    println("3. Actor instance is created with Properties")
    println("4. Actor is registered with time manager")
    println("5. Simulation begins...")
    
    // Example of command handling
    println("\n📡 Command Processing Example:")
    println("Command: setTargetTemperature(22.5)")
    println("→ Actor receives ActorInteractionEvent")
    println("→ handleSetTargetTemperature() is called")
    println("→ State machine transitions: Idle → Heating")
    println("→ Event emitted: heatingStarted")
    println("→ Telemetry updated: hvacStatus")
    
    // Example of spontaneous behavior
    println("\n⚡ Spontaneous Behavior Example:")
    println("Tick: 1000")
    println("→ Rules engine evaluates conditions")
    println("→ Temperature reached target: targetReached event")
    println("→ State transition: Heating → Maintaining")
    println("→ Telemetry emission: temperatureReading")
    
  /**
   * Example 4: Generate multiple actors from a directory of models
   */
  def generateActorsFromDirectory(): Unit =
    println("\n=== Batch Actor Generation ===")
    
    // This would scan a directory for *.htcdl.json files and generate actors
    val modelDirectory = "src/main/resources/examples/"
    val outputDirectory = "target/generated-sources/"
    
    println(s"📁 Scanning directory: $modelDirectory")
    println(s"🎯 Output directory: $outputDirectory")
    
    // In a real implementation, this would:
    // 1. Find all *.htcdl.json files
    // 2. Parse each model
    // 3. Generate corresponding actor class
    // 4. Create a registry/factory for actor creation
    
    println("✅ Batch generation would process:")
    println("   - car-model.htcdl.json → IntelligentSimulatedCarActor.scala")
    println("   - drone-model.htcdl.json → AutonomousDroneActor.scala")
    println("   - sensor-model.htcdl.json → SmartSensorActor.scala")

  /**
   * Example 5: Runtime actor creation and configuration
   */
  def runtimeActorConfiguration(): Unit =
    println("\n=== Runtime Actor Configuration ===")
    
    // Show how actors would be configured at runtime
    val runtimeConfig = Map(
      "telemetry.emission.interval" -> "5000",
      "rules.evaluation.enabled" -> "true", 
      "statemachine.validation.strict" -> "true",
      "logging.level" -> "DEBUG"
    )
    
    println("⚙️ Runtime Configuration:")
    runtimeConfig.foreach { case (key, value) =>
      println(s"   $key = $value")
    }
    
    println("\n🔧 Actor Customization Points:")
    println("   - Override actModelSpontaneous() for custom logic")
    println("   - Override actModelInteraction() for custom commands")  
    println("   - Override actModelTimeStep() for time-stepped behavior")
    println("   - Configure telemetry emission intervals")
    println("   - Add custom rules and conditions")

  private def createMockProperties(): MockProperties =
    // This is a simplified mock - real Properties would come from HTC simulator
    MockProperties(
      entityId = "thermostat-001",
      resourceId = "building-a-floor-1",
      actorType = "LoadBalancedDistributed",
      data = null,
      dependencies = mutable.Map.empty,
      reporters = mutable.Map.empty,
      timeManager = null,
      creatorManager = null,
      timePolicy = Some("EventDrivenSimulation")
    )

  /**
   * Run all examples
   */
  def main(args: Array[String]): Unit =
    println("🚀 HTC Digital Twin Language - Integration Examples")
    println("=" * 60)
    
    try
      generateActorFromFile()
      createModelAndGenerateActor()
      simulateHtcIntegration()
      generateActorsFromDirectory()
      runtimeActorConfiguration()
      
      println("\n🎉 All integration examples completed successfully!")
      println("\n📚 Next Steps:")
      println("1. Add generated actors to your HTC simulator project")
      println("2. Configure the actor registry in your application")
      println("3. Set up model file scanning and actor generation pipeline")
      println("4. Implement custom behavior by extending generated actors")
      println("5. Configure telemetry and monitoring for your digital twins")
      
    catch
      case ex: Exception =>
        println(s"\n💥 Error during integration examples: ${ex.getMessage}")
        ex.printStackTrace()