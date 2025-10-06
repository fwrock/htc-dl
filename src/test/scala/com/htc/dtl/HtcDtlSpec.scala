package com.htc.dtl

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.htc.dtl.model.*
import com.htc.dtl.validation.*
import io.circe.Json
import scala.io.Source

class HtcDtlSpec extends AnyFlatSpec with Matchers:

  "HtcDtl" should "parse a valid HTCDL model from string" in {
    val jsonString = Source.fromResource("examples/car-model.htcdl.json").mkString
    
    val result = HtcDtl.parse(jsonString)
    
    result shouldBe a[Right[_, _]]
    val model = result.getOrElse(fail("Should have parsed successfully"))
    
    model.displayName shouldEqual "Intelligent Simulated Car"
    model.`@id` shouldEqual "dtmi:htc:mobility:car;1"
    model.`@type` shouldEqual "Interface"
  }

  it should "validate model references correctly" in {
    val jsonString = Source.fromResource("examples/car-model.htcdl.json").mkString
    val model = HtcDtl.parse(jsonString).getOrElse(fail("Should parse"))
    
    // All references should be valid in the example model
    val validationResult = HtcDtl.validate(model)
    validationResult shouldBe a[Right[_, _]]
  }

  it should "detect invalid command references in state machine" in {
    val model = HtcModelBuilder("dtmi:test:invalid;1", "Test", "Invalid model")
      .addCommand(HtcUtils.command("validCommand"))
      .withStateMachine(StateMachine(
        initialState = "State1",
        states = List(HtcUtils.state("State1"), HtcUtils.state("State2")),
        transitions = List(
          HtcUtils.transitionOnCommand("State1", "State2", "invalidCommand")
        )
      ))
      .build()

    val result = HtcDtl.validate(model)
    
    result shouldBe a[Left[_, _]]
    val errors = result.left.getOrElse(fail("Should have validation errors"))
    errors should contain(ValidationError.InvalidReference("Trigger", "invalidCommand", "Command", "invalidCommand"))
  }

  it should "detect duplicate names" in {
    val model = HtcModelBuilder("dtmi:test:duplicates;1", "Test", "Duplicate names")
      .addProperty(HtcUtils.property("duplicateName", "string"))
      .addProperty(HtcUtils.property("duplicateName", "double"))
      .build()

    val result = HtcDtl.validate(model)
    
    result shouldBe a[Left[_, _]]
    val errors = result.left.getOrElse(fail("Should have validation errors"))
    errors should contain(ValidationError.DuplicateName("Property", "duplicateName"))
  }

  it should "serialize model back to JSON" in {
    val model = HtcModelBuilder("dtmi:test:simple;1", "Simple Test", "A simple test model")
      .withVersion("1.0.0")
      .addProperty(HtcUtils.property("testProp", "string", writable = true))
      .addCommand(HtcUtils.command("testCommand", IntentType.Control, ExecutionMode.Sync))
      .build()

    val json = HtcDtl.toJson(model)
    
    json should include("dtmi:test:simple;1")
    json should include("Simple Test")
    json should include("testProp")
    json should include("testCommand")
  }

  it should "analyze model statistics correctly" in {
    val jsonString = Source.fromResource("examples/car-model.htcdl.json").mkString
    val model = HtcDtl.parse(jsonString).getOrElse(fail("Should parse"))
    
    val stats = HtcDtl.analyze(model)
    
    stats.propertyCount shouldEqual 3
    stats.commandCount shouldEqual 3
    stats.eventCount shouldEqual 4
    stats.stateCount shouldEqual 3
    stats.transitionCount shouldEqual 2
    stats.hasStateMachine shouldBe true
    stats.hasPhysics shouldBe true
  }

class HtcModelBuilderSpec extends AnyFlatSpec with Matchers:

  "HtcModelBuilder" should "create a valid minimal model" in {
    val model = HtcModelBuilder("dtmi:test:minimal;1", "Minimal Test", "A minimal test model")
      .build()

    model.`@id` shouldEqual "dtmi:test:minimal;1"
    model.displayName shouldEqual "Minimal Test"
    model.description shouldEqual "A minimal test model"
    model.`@type` shouldEqual "Interface"
    model.`@context` shouldEqual "dtmi:htc:context;1"
  }

  it should "build a complex model with all components" in {
    val model = HtcModelBuilder("dtmi:test:complex;1", "Complex Test", "A complex test model")
      .withVersion("2.0.0", Some("Added new features"))
      .addProperty(HtcUtils.property("status", "string"))
      .addTelemetry(HtcUtils.telemetry("temperature", "double", Some("celsius")))
      .addCommand(HtcUtils.command("start"))
      .addEvent(HtcUtils.event("started"))
      .withStateMachine(StateMachine(
        initialState = "Idle",
        states = List(HtcUtils.state("Idle"), HtcUtils.state("Running")),
        transitions = List(HtcUtils.transitionOnCommand("Idle", "Running", "start", Some("started")))
      ))
      .addRule(HtcUtils.rule("TestRule", "temperature > 100", "overheated"))
      .addGoal(HtcUtils.goal("efficiency", 0.8))
      .addAiModel(HtcUtils.aiModel("predictor", "prediction", "http://example.com/model"))
      .build()

    model.`@versionInfo` should not be empty
    model.properties should not be empty
    model.telemetry should not be empty
    model.commands should not be empty
    model.events should not be empty
    model.stateMachine should not be empty
    model.rules should not be empty
    model.goals should not be empty
    model.aiModels should not be empty
  }

class ValidationSpec extends AnyFlatSpec with Matchers:

  "Validator" should "accept valid DTMIs" in {
    val validDtmis = List(
      "dtmi:htc:test;1",
      "dtmi:com:example:device;2.1",
      "dtmi:org:company:product:component;1.0.0"
    )

    validDtmis.foreach { dtmi =>
      val result = Validators.validateDtmi(dtmi)
      result shouldBe a[cats.data.Validated.Valid[_]]
    }
  }

  it should "reject invalid DTMIs" in {
    val invalidDtmis = List(
      "invalid",
      "dtmi:",
      "dtmi:test",
      "dtmi:test;",
      "dtmi:test;0",
      "dtmi:123invalid;1"
    )

    invalidDtmis.foreach { dtmi =>
      val result = Validators.validateDtmi(dtmi)
      result shouldBe a[cats.data.Validated.Invalid[_]]
    }
  }

  it should "validate state machine reachability" in {
    val reachableStateMachine = StateMachine(
      initialState = "A",
      states = List(HtcUtils.state("A"), HtcUtils.state("B"), HtcUtils.state("C")),
      transitions = List(
        HtcUtils.transitionOnCommand("A", "B", "cmd1"),
        HtcUtils.transitionOnCommand("B", "C", "cmd2")
      )
    )

    val model = HtcModelBuilder("dtmi:test:reachable;1", "Test", "Test")
      .addCommand(HtcUtils.command("cmd1"))
      .addCommand(HtcUtils.command("cmd2"))
      .withStateMachine(reachableStateMachine)
      .build()

    val result = HtcDtl.validate(model)
    result shouldBe a[Right[_, _]]
  }

  it should "detect unreachable states" in {
    val unreachableStateMachine = StateMachine(
      initialState = "A",
      states = List(HtcUtils.state("A"), HtcUtils.state("B"), HtcUtils.state("C")),
      transitions = List(
        HtcUtils.transitionOnCommand("A", "B", "cmd1")
        // State C is unreachable
      )
    )

    val model = HtcModelBuilder("dtmi:test:unreachable;1", "Test", "Test")
      .addCommand(HtcUtils.command("cmd1"))
      .withStateMachine(unreachableStateMachine)
      .build()

    val result = HtcDtl.validate(model)
    result shouldBe a[Left[_, _]]
  }

class UtilsSpec extends AnyFlatSpec with Matchers:

  "HtcUtils" should "create properties with correct types" in {
    val prop = HtcUtils.property("testProp", "string", writable = true, unit = Some("celsius"))
    
    prop.name shouldEqual "testProp"
    prop.writable shouldBe true
    prop.unit shouldEqual Some("celsius")
    prop.schema shouldEqual Json.fromString("string")
  }

  it should "create commands with correct configuration" in {
    val cmd = HtcUtils.command("testCmd", IntentType.Query, ExecutionMode.Sync)
    
    cmd.name shouldEqual "testCmd"
    cmd.intent shouldEqual Some(IntentType.Query)
    cmd.executionMode shouldEqual Some(ExecutionMode.Sync)
  }

  it should "create transitions with proper triggers" in {
    val transition = HtcUtils.transitionOnCommand("From", "To", "triggerCmd", Some("resultEvent"))
    
    transition.from shouldEqual "From"
    transition.to shouldEqual "To"
    transition.trigger.command shouldEqual Some("triggerCmd")
    transition.action.flatMap(_.emitEvent) shouldEqual Some("resultEvent")
  }