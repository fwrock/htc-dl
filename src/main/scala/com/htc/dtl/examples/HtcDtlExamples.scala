package com.htc.dtl.examples

import com.htc.dtl.*
import com.htc.dtl.model.*
import com.htc.dtl.parser.*
import io.circe.Json

/**
 * Exemplos práticos de uso da biblioteca HTC DTL.
 * 
 * Este arquivo demonstra como usar a biblioteca para:
 * 1. Carregar e validar modelos existentes
 * 2. Criar modelos programaticamente
 * 3. Analisar e manipular modelos
 * 4. Integrar com simuladores
 */
object HtcDtlExamples:

  // Exemplo 1: Carregando e validando um modelo existente
  def loadAndValidateModel(): Unit =
    println("=== Exemplo 1: Carregando modelo do carro ===")
    
    val result = HtcDtl.parseFile("src/main/resources/examples/car-model.htcdl.json")
    
    result match
      case Right(model) =>
        println(s"✅ Modelo '${model.displayName}' carregado com sucesso!")
        
        // Analisar o modelo
        val stats = HtcDtl.analyze(model)
        println(s"📊 Estatísticas:")
        println(s"   - Propriedades: ${stats.propertyCount}")
        println(s"   - Telemetrias: ${stats.telemetryCount}")
        println(s"   - Comandos: ${stats.commandCount}")
        println(s"   - Eventos: ${stats.eventCount}")
        println(s"   - Estados: ${stats.stateCount}")
        println(s"   - Transições: ${stats.transitionCount}")
        
        // Verificar elementos não utilizados
        val unused = HtcDtl.findUnused(model)
        if unused.unusedEvents.nonEmpty then
          println(s"⚠️  Eventos não utilizados: ${unused.unusedEvents.mkString(", ")}")
        else
          println("✅ Todos os eventos estão sendo utilizados")
          
      case Left(error) =>
        println(s"❌ Erro ao carregar modelo:")
        println(ErrorFormatter.formatParseError(error))

  // Exemplo 2: Criando um modelo de drone programaticamente
  def createDroneModel(): HtcModel =
    println("\n=== Exemplo 2: Criando modelo de drone ===")
    
    val droneModel = HtcModelBuilder("dtmi:htc:mobility:drone;1", "Autonomous Drone", "An intelligent autonomous drone for surveillance")
      .withVersion("1.0.0", Some("Initial drone model with full autonomy"))
      
      // Propriedades do estado
      .addProperty(HtcUtils.property("batteryLevel", "double", unit = Some("percent")))
      .addProperty(HtcUtils.property("altitude", "double", unit = Some("metre")))
      .addProperty(HtcUtils.property("isArmed", "boolean", writable = true))
      .addProperty(HtcUtils.property("flightMode", "string", writable = true))
      
      // Telemetrias
      .addTelemetry(Telemetry(
        name = "gpsPosition",
        schema = Json.obj(
          "@type" -> Json.fromString("Object"),
          "fields" -> Json.arr(
            Json.obj("name" -> Json.fromString("latitude"), "schema" -> Json.fromString("double")),
            Json.obj("name" -> Json.fromString("longitude"), "schema" -> Json.fromString("double")),
            Json.obj("name" -> Json.fromString("altitude"), "schema" -> Json.fromString("double"))
          )
        ),
        emissionProfile = Some(EmissionProfile(EmissionType.Periodic, Some(1.0), Some("perSecond")))
      ))
      .addTelemetry(Telemetry(
        name = "batteryStatus",
        schema = Json.fromString("double"),
        unit = Some("percent"),
        emissionProfile = Some(EmissionProfile(EmissionType.OnChange, tolerance = Some(5.0)))
      ))
      
      // Comandos
      .addCommand(Command(
        name = "takeOff",
        intent = Some(IntentType.Control),
        executionMode = Some(ExecutionMode.Async),
        requestSchema = Some(Json.obj(
          "name" -> Json.fromString("takeOffParams"),
          "@type" -> Json.fromString("Object"),
          "fields" -> Json.arr(
            Json.obj("name" -> Json.fromString("targetAltitude"), "schema" -> Json.fromString("double"), "unit" -> Json.fromString("metre"))
          )
        )),
        completionEvents = Some(CompletionEvents(Some("takeOffCompleted"), Some("takeOffFailed")))
      ))
      .addCommand(HtcUtils.command("land", IntentType.Control, ExecutionMode.Async))
      .addCommand(HtcUtils.command("returnToBase", IntentType.Control, ExecutionMode.Async))
      .addCommand(HtcUtils.command("emergencyStop", IntentType.Control, ExecutionMode.Sync))
      
      // Eventos
      .addEvent(HtcUtils.event("takeOffCompleted"))
      .addEvent(HtcUtils.event("takeOffFailed"))
      .addEvent(HtcUtils.event("landingCompleted"))
      .addEvent(HtcUtils.event("lowBattery"))
      .addEvent(HtcUtils.event("emergencyLanding"))
      .addEvent(HtcUtils.event("obstacleDetected"))
      
      // Máquina de estado
      .withStateMachine(StateMachine(
        initialState = "Grounded",
        states = List(
          HtcUtils.state("Grounded"),
          HtcUtils.state("TakingOff"),
          HtcUtils.state("Flying"),
          HtcUtils.state("Landing"),
          HtcUtils.state("Emergency")
        ),
        transitions = List(
          HtcUtils.transitionOnCommand("Grounded", "TakingOff", "takeOff"),
          HtcUtils.transitionOnEvent("TakingOff", "Flying", "takeOffCompleted"),
          HtcUtils.transitionOnEvent("TakingOff", "Grounded", "takeOffFailed"),
          HtcUtils.transitionOnCommand("Flying", "Landing", "land"),
          HtcUtils.transitionOnCommand("Flying", "Landing", "returnToBase"),
          HtcUtils.transitionOnEvent("Landing", "Grounded", "landingCompleted"),
          HtcUtils.transitionOnCommand("Flying", "Emergency", "emergencyStop"),
          HtcUtils.transitionOnEvent("Flying", "Emergency", "lowBattery"),
          HtcUtils.transitionOnEvent("Emergency", "Grounded", "emergencyLanding")
        )
      ))
      
      // Física
      .withPhysics(Physics(
        mass = Some(Mass(2.5, "kilogram")),
        dimensions = Some(Dimensions(0.8, 0.8, 0.3, "metre"))
      ))
      
      // Regras autônomas
      .addRule(Rule(
        name = "LowBatteryRule",
        condition = "properties.batteryLevel < 20.0",
        action = Action(emitEvent = Some("lowBattery"))
      ))
      .addRule(Rule(
        name = "CriticalBatteryRule", 
        condition = "properties.batteryLevel < 10.0",
        action = Action(emitEvent = Some("emergencyLanding"))
      ))
      
      // Objetivos de IA
      .addGoal(HtcUtils.goal("maintainSafeAltitude", 1.0))
      .addGoal(HtcUtils.goal("conserveBattery", 0.8))
      .addGoal(HtcUtils.goal("avoidObstacles", 1.0))
      
      // Modelos de IA
      .addAiModel(AiModel(
        name = "obstacleAvoidanceModel",
        purpose = "obstacle detection and avoidance",
        modelUri = "htc-models://ai/obstacle_avoidance_v2.onnx",
        inputSchema = Some("dtmi:htc:schemas:sensorData;1"),
        outputSchema = Some("dtmi:htc:schemas:navigationCommand;1")
      ))
      .addAiModel(AiModel(
        name = "pathPlanningModel",
        purpose = "optimal path planning",
        modelUri = "htc-models://ai/path_planner_v1.onnx"
      ))
      
      .build()

    // Validar o modelo criado
    HtcDtl.validate(droneModel) match
      case Right(validModel) =>
        println("✅ Modelo de drone criado e validado com sucesso!")
        val stats = HtcDtl.analyze(validModel)
        println(s"📊 Modelo possui ${stats.stateCount} estados e ${stats.transitionCount} transições")
        validModel
      case Left(errors) =>
        println("❌ Erros de validação encontrados:")
        errors.foreach(error => println(s"   - ${ErrorFormatter.formatValidationError(error)}"))
        throw new RuntimeException("Modelo inválido")

  // Exemplo 3: Simulação de integração com simulador
  def simulateIntegration(model: HtcModel): Unit =
    println("\n=== Exemplo 3: Simulação de integração ===")
    
    // Extrair informações úteis para o simulador
    val commands = model.commands.getOrElse(List.empty)
    val events = model.events.getOrElse(List.empty)
    val stateMachine = model.stateMachine
    
    println(s"🎮 Comandos disponíveis para simulação:")
    commands.foreach { cmd =>
      val mode = cmd.executionMode.getOrElse(ExecutionMode.Sync)
      val intent = cmd.intent.getOrElse(IntentType.Control)
      println(s"   - ${cmd.name} (${intent}, ${mode})")
    }
    
    println(s"📡 Eventos que o simulador deve monitorar:")
    events.foreach(event => println(s"   - ${event.name}"))
    
    stateMachine.foreach { sm =>
      println(s"🔄 Estados da máquina de estado:")
      sm.states.foreach(state => println(s"   - ${state.name}"))
      
      println(s"🔀 Transições implementadas:")
      sm.transitions.foreach { transition =>
        val trigger = transition.trigger.command
          .map(cmd => s"comando: $cmd")
          .orElse(transition.trigger.event.map(evt => s"evento: $evt"))
          .getOrElse("condição customizada")
        println(s"   - ${transition.from} → ${transition.to} (trigger: $trigger)")
      }
    }

  // Exemplo 4: Análise comparativa de modelos
  def compareModels(): Unit =
    println("\n=== Exemplo 4: Análise comparativa ===")
    
    // Criar um modelo simples para comparação
    val simpleModel = HtcModelBuilder("dtmi:htc:simple:sensor;1", "Simple Sensor", "A basic temperature sensor")
      .addProperty(HtcUtils.property("temperature", "double", unit = Some("celsius")))
      .addTelemetry(HtcUtils.telemetry("temperatureReading", "double", unit = Some("celsius")))
      .addCommand(HtcUtils.command("calibrate"))
      .build()
    
    val carResult = HtcDtl.parseFile("src/main/resources/examples/car-model.htcdl.json")
    
    carResult match
      case Right(carModel) =>
        val carStats = HtcDtl.analyze(carModel)
        val sensorStats = HtcDtl.analyze(simpleModel)
        
        println("📊 Comparação de complexidade:")
        println(f"${"Modelo"}%-20s ${"Comandos"}%-10s ${"Estados"}%-10s ${"Regras"}%-10s ${"Complexidade"}%-15s")
        println("-" * 70)
        
        val carComplexity = carStats.commandCount + carStats.stateCount + carStats.ruleCount + carStats.aiModelCount
        val sensorComplexity = sensorStats.commandCount + sensorStats.stateCount + sensorStats.ruleCount + sensorStats.aiModelCount
        
        println(f"${carModel.displayName}%-20s ${carStats.commandCount}%-10s ${carStats.stateCount}%-10s ${carStats.ruleCount}%-10s $carComplexity%-15s")
        println(f"${simpleModel.displayName}%-20s ${sensorStats.commandCount}%-10s ${sensorStats.stateCount}%-10s ${sensorStats.ruleCount}%-10s $sensorComplexity%-15s")
        
      case Left(error) =>
        println("❌ Não foi possível carregar o modelo do carro para comparação")

  // Exemplo 5: Serialização e persistência
  def demonstrateSerialization(model: HtcModel): Unit =
    println("\n=== Exemplo 5: Serialização ===")
    
    // Serializar para JSON
    val json = HtcDtl.toJson(model)
    println(s"📄 Modelo serializado (${json.length} caracteres)")
    
    // Simular salvamento e recarregamento
    val tempFile = "temp-model.htcdl.json"
    ModelSerializer.writeToFile(model, tempFile) match
      case scala.util.Success(_) =>
        println(s"💾 Modelo salvo em: $tempFile")
        
        // Recarregar e validar
        HtcDtl.parseFile(tempFile) match
          case Right(reloadedModel) =>
            println("✅ Modelo recarregado e validado com sucesso!")
            
            // Verificar se são equivalentes
            val originalStats = HtcDtl.analyze(model)
            val reloadedStats = HtcDtl.analyze(reloadedModel)
            
            if originalStats == reloadedStats then
              println("✅ Modelos são equivalentes após serialização/deserialização")
            else
              println("⚠️  Diferenças detectadas após serialização")
              
          case Left(error) =>
            println(s"❌ Erro ao recarregar modelo: $error")
            
      case scala.util.Failure(exception) =>
        println(s"❌ Erro ao salvar modelo: ${exception.getMessage}")

  // Executar todos os exemplos
  def runExamples(): Unit =
    println("🚀 HTC Digital Twin Language - Exemplos de Uso")
    println("=" * 50)
    
    try
      // Exemplo 1: Carregar modelo existente
      loadAndValidateModel()
      
      // Exemplo 2: Criar modelo programaticamente
      val droneModel = createDroneModel()
      
      // Exemplo 3: Simular integração
      simulateIntegration(droneModel)
      
      // Exemplo 4: Análise comparativa
      compareModels()
      
      // Exemplo 5: Serialização
      demonstrateSerialization(droneModel)
      
      println("\n🎉 Todos os exemplos executados com sucesso!")
      
    catch
      case ex: Exception =>
        println(s"\n💥 Erro durante execução: ${ex.getMessage}")
        ex.printStackTrace()

  def main(args: Array[String]): Unit = runExamples()