# HTC DTL - Quick Integration Guide

## 🚀 Get Started in 5 Minutes

### 1. Add Dependency

```scala
// build.sbt
libraryDependencies += "com.htc" %% "htc-dl" % "0.1.0-SNAPSHOT"
```

### 2. Basic Parsing

```scala
import com.htc.dtl.HtcDtl

// Load from file
val model = HtcDtl.parseFile("my-model.htcdl.json").getOrThrow

// Validate model
HtcDtl.validate(model) match
  case Right(validModel) => println("✅ Valid model!")
  case Left(errors) => errors.foreach(println)
```

### 3. Create Model Programmatically

```scala
import com.htc.dtl.{HtcModelBuilder, HtcUtils}
import com.htc.dtl.model.*

val model = HtcModelBuilder("dtmi:my:entity;1", "My Entity", "Description")
  .addProperty(HtcUtils.property("temperature", "double", unit = Some("celsius")))
  .addCommand(HtcUtils.command("turnOn", IntentType.Control))
  .addEvent(HtcUtils.event("turnedOn"))
  .withStateMachine(StateMachine(
    initialState = "Off",
    states = List(HtcUtils.state("Off"), HtcUtils.state("On")),
    transitions = List(HtcUtils.transitionOnCommand("Off", "On", "turnOn", Some("turnedOn")))
  ))
  .build()
```

### 4. Simulator Integration

```scala
// Extract information for simulator
val commands = model.commands.getOrElse(List.empty)
val events = model.events.getOrElse(List.empty)
val stateMachine = model.stateMachine

// Implement handlers
commands.foreach { command =>
  simulator.registerCommandHandler(command.name) { params =>
    // Command logic
  }
}

stateMachine.foreach { fsm =>
  simulator.setStateMachine(fsm.initialState, fsm.transitions)
}
```

## 📊 Model Analysis

```scala
val stats = HtcDtl.analyze(model)
println(s"Commands: ${stats.commandCount}")
println(s"States: ${stats.stateCount}")

val unused = HtcDtl.findUnused(model)
if unused.unusedEvents.nonEmpty then
  println(s"Unused events: ${unused.unusedEvents}")
```

## 🔧 Common Use Cases

### CI/CD Pipeline Validation

```scala
def validateModel(path: String): Boolean =
  HtcDtl.parseFile(path) match
    case Right(_) => true
    case Left(error) =>
      println(s"❌ Error: ${ErrorFormatter.formatParseError(error)}")
      false
```

### Code Generation

```scala
def generateActorCode(model: HtcModel): String =
  val commands = model.commands.getOrElse(List.empty)
  val states = model.stateMachine.map(_.states).getOrElse(List.empty)
  
  s"""
  |class ${model.displayName}Actor extends Actor:
  |  ${commands.map(cmd => s"def handle${cmd.name.capitalize}() = ???").mkString("\n  ")}
  |""".stripMargin
```

### Model Transformation

```scala
def addMonitoringToModel(model: HtcModel): HtcModel =
  model.copy(
    telemetry = Some(model.telemetry.getOrElse(List.empty) ++ List(
      HtcUtils.telemetry("heartbeat", "boolean"),
      HtcUtils.telemetry("lastActivity", "dateTime")
    ))
  )
```

## ⚡ Performance Tips

- Use `HtcDtl.parseFile()` for small files (< 1MB)
- For large files, consider streaming
- Cache validated models in production
- Use `ModelStatistics` for complexity metrics

## 🐛 Debug and Troubleshooting

```scala
// Verbose error reporting
result match
  case Left(error) =>
    println(ErrorFormatter.formatParseError(error))
    // Detailed logging for debug
    error match
      case ParseError.ValidationErrors(errors) =>
        errors.foreach(e => logger.debug(s"Validation: $e"))
      case _ => logger.debug(s"Parse error: $error")
```

## 📈 Monitoring

```scala
// Metrics for observability
val stats = HtcDtl.analyze(model)
metrics.gauge("htc.model.complexity", stats.commandCount + stats.stateCount)
metrics.gauge("htc.model.states", stats.stateCount)
metrics.counter("htc.model.loaded").increment()
```