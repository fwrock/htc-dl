# HTC Digital Twin Language (HTCDL) - Core Library

A Scala library for parsing, validation and manipulation of HTCDL (HTC Digital Twin Language) models.

## 🚀 Overview

This library is the "compiler" for the HTCDL language, transforming JSON files into strongly typed and validated Scala objects. It serves as the foundation for the HTC simulator and other ecosystem tools.

## 📦 Features

- **Robust Parsing**: JSON to Scala case classes conversion using Circe
- **Comprehensive Validation**: Reference, structure and semantic verification
- **Intuitive API**: Simple interface for use in other projects
- **Model Analysis**: Statistics and unused element detection
- **Builder Pattern**: Programmatic HTCDL model creation
- **Extensibility**: Modular architecture for future extensions

## 🏗️ Architecture

```
com.htc.dtl/
├── model/           # Case classes representing the HTCDL model
├── parser/          # Main parser and utilities
├── validation/      # Validation system with business rules
└── HtcDtl.scala    # Main API
```

## 📖 Basic Usage

### Parsing a model

```scala
import com.htc.dtl.HtcDtl

// From file
val result = HtcDtl.parseFile("model.htcdl.json")

result match
  case Right(model) => 
    println(s"Model ${model.displayName} loaded successfully!")
    println(s"States: ${model.stateMachine.map(_.states.size).getOrElse(0)}")
  case Left(error) => 
    println(s"Error loading model: $error")

// From JSON string
val jsonString = """{ "@context": "dtmi:htc:context;1", ... }"""
val model = HtcDtl.parse(jsonString).getOrThrow
```

### Programmatic model creation

```scala
import com.htc.dtl.{HtcModelBuilder, HtcUtils}
import com.htc.dtl.model.*

val model = HtcModelBuilder("dtmi:htc:robot;1", "Smart Robot", "An intelligent robot")
  .withVersion("1.0.0", Some("Initial version"))
  .addProperty(HtcUtils.property("batteryLevel", "double", unit = Some("percent")))
  .addTelemetry(HtcUtils.telemetry("position", "dtmi:geo:position;1"))
  .addCommand(HtcUtils.command("move", IntentType.Control, ExecutionMode.Async))
  .addEvent(HtcUtils.event("batteryLow"))
  .withStateMachine(StateMachine(
    initialState = "Idle",
    states = List(
      HtcUtils.state("Idle"),
      HtcUtils.state("Moving")
    ),
    transitions = List(
      HtcUtils.transitionOnCommand("Idle", "Moving", "move")
    )
  ))
  .addRule(HtcUtils.rule("BatteryWarning", "batteryLevel < 10", "batteryLow"))
  .build()

// Validate the model
val validationResult = HtcDtl.validate(model)
validationResult match
  case Right(validModel) => println("Valid model!")
  case Left(errors) => errors.foreach(println)
```

### Model analysis

```scala
// Get statistics
val stats = HtcDtl.analyze(model)
println(s"Properties: ${stats.propertyCount}")
println(s"Commands: ${stats.commandCount}")
println(s"Has state machine: ${stats.hasStateMachine}")

// Find unused elements
val unused = HtcDtl.findUnused(model)
if unused.unusedEvents.nonEmpty then
  println(s"Unused events: ${unused.unusedEvents.mkString(", ")}")
```

### Serialization

```scala
// Convert to JSON
val json = HtcDtl.toJson(model)
println(json)

// Save to file
import com.htc.dtl.parser.ModelSerializer
ModelSerializer.writeToFile(model, "output.htcdl.json")
```

## 🔍 Validation System

The library includes a robust validation system that checks:

### Basic Structure
- ✅ Valid JSON-LD context
- ✅ Well-formed DTMI
- ✅ Required fields present
- ✅ Correct data types

### References
- ✅ Commands referenced in transitions exist
- ✅ Referenced events exist
- ✅ Referenced states exist
- ✅ Referenced schemas are defined

### Semantics
- ✅ Unique names within each collection
- ✅ Reachable states in state machine
- ✅ Valid triggers in transitions
- ✅ Command completion events exist

### Validation Example

```scala
val errors = List(
  ValidationError.InvalidReference("Trigger", "invalidCommand", "Command", "invalidCommand"),
  ValidationError.DuplicateName("Property", "duplicatedName"),
  ValidationError.MissingRequiredField("HtcModel", "displayName")
)

// Automatic error formatting
errors.foreach(error => println(ErrorFormatter.formatValidationError(error)))
```

## 🧪 Testing

```bash
sbt test
```

Tests cover:
- Parsing of valid and invalid models
- All validation rules
- Builder pattern
- Serialization/deserialization
- Model analysis

## 📊 Model Metrics

The library offers automatic analysis:

```scala
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
```

## 🔧 Setup as Dependency

To use this library in other Scala projects:

```scala
// build.sbt
libraryDependencies += "com.htc" %% "htc-dl" % "0.1.0-SNAPSHOT"
```

## 🎯 Next Steps

1. **Simulator Integration**: Use this library as foundation for HTC simulator
2. **Code Generators**: Create generators for different targets (Akka, ZIO, etc.)
3. **Tooling**: CLI tools for model validation and analysis
4. **IDE Support**: VS Code plugin with syntax highlighting and validation
5. **JSON Schemas**: Generate JSON Schema for editor validation

## 📝 Examples

See the `src/main/resources/examples/` folder for complete HTCDL model examples.

## 🤝 Contributing

This library follows HTCDL language standards and should maintain compatibility with the official specification.

---

**HTC Digital Twin Language Core Library** - Transforming JSON descriptions into intelligent digital systems.# htc-dl
