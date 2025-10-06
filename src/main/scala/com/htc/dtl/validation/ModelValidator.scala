package com.htc.dtl.validation

import com.htc.dtl.model.*
import cats.data.ValidatedNel
import cats.implicits.*

// Validation error types
enum ValidationError:
  case InvalidReference(elementType: String, name: String, referencedType: String, referencedName: String)
  case DuplicateName(elementType: String, name: String)
  case InvalidStateTransition(from: String, to: String, reason: String)
  case MissingRequiredField(elementType: String, fieldName: String)
  case InvalidFieldValue(elementType: String, fieldName: String, value: String, reason: String)
  case InvalidContext(expectedContext: String, actualContext: String)
  case InvalidDtmi(dtmi: String, reason: String)
  case CircularReference(path: List[String])

type ValidationResult[T] = ValidatedNel[ValidationError, T]

// Validator trait
trait Validator[T]:
  def validate(model: T): ValidationResult[T]

// Core validation functions
object Validators:
  
  // DTMI validation
  def validateDtmi(dtmi: String): ValidationResult[String] =
    val dtmiPattern = """^dtmi:[a-zA-Z][a-zA-Z0-9_]*(?::[a-zA-Z][a-zA-Z0-9_]*)*;[1-9][0-9]*(?:\.[0-9]+)*$""".r
    dtmiPattern.findFirstIn(dtmi) match
      case Some(_) => dtmi.validNel
      case None => ValidationError.InvalidDtmi(dtmi, "DTMI format is invalid").invalidNel

  // Context validation
  def validateContext(actualContext: String, expectedContext: String = "dtmi:htc:context;1"): ValidationResult[String] =
    if actualContext == expectedContext then
      actualContext.validNel
    else
      ValidationError.InvalidContext(expectedContext, actualContext).invalidNel

  // Check for duplicate names in a collection
  def checkDuplicateNames[T](items: List[T], getName: T => String, elementType: String): ValidationResult[List[T]] =
    val names = items.map(getName)
    val duplicates = names.groupBy(identity).filter(_._2.size > 1).keys.toList
    
    if duplicates.isEmpty then
      items.validNel
    else
      duplicates.map(name => ValidationError.DuplicateName(elementType, name)).toNel
        .fold(items.validNel)(_.invalid)

  // Reference validation helpers
  def validateReference(
    referenceName: String,
    availableNames: List[String], 
    elementType: String,
    referencedType: String
  ): ValidationResult[String] =
    if availableNames.contains(referenceName) then
      referenceName.validNel
    else
      ValidationError.InvalidReference(elementType, referenceName, referencedType, referenceName).invalidNel

// Main model validator
given Validator[HtcModel] = new Validator[HtcModel]:
  
  def validate(model: HtcModel): ValidationResult[HtcModel] =
    val validations = List(
      validateBasicStructure(model),
      validateReferences(model),
      validateStateMachine(model),
      validateSemantics(model)
    )
    
    validations.sequence.map(_ => model)

  private def validateBasicStructure(model: HtcModel): ValidationResult[Unit] =
    (
      Validators.validateContext(model.`@context`),
      Validators.validateDtmi(model.`@id`),
      validateRequiredFields(model)
    ).mapN((_, _, _) => ())

  private def validateRequiredFields(model: HtcModel): ValidationResult[Unit] =
    val validations = List(
      if model.displayName.nonEmpty then ().validNel 
      else ValidationError.MissingRequiredField("HtcModel", "displayName").invalidNel,
      
      if model.description.nonEmpty then ().validNel
      else ValidationError.MissingRequiredField("HtcModel", "description").invalidNel,
      
      if model.`@type` == "Interface" then ().validNel
      else ValidationError.InvalidFieldValue("HtcModel", "@type", model.`@type`, "Must be 'Interface'").invalidNel
    )
    
    validations.sequence.map(_ => ())

  private def validateReferences(model: HtcModel): ValidationResult[Unit] =
    val commandNames = model.commands.getOrElse(List.empty).map(_.name)
    val eventNames = model.events.getOrElse(List.empty).map(_.name)
    val propertyNames = model.properties.getOrElse(List.empty).map(_.name)
    val stateNames = model.stateMachine.map(_.states.map(_.name)).getOrElse(List.empty)
    
    val validations = List(
      // Check for duplicate names within each collection
      Validators.checkDuplicateNames(model.properties.getOrElse(List.empty), _.name, "Property"),
      Validators.checkDuplicateNames(model.telemetry.getOrElse(List.empty), _.name, "Telemetry"),
      Validators.checkDuplicateNames(model.commands.getOrElse(List.empty), _.name, "Command"),
      Validators.checkDuplicateNames(model.events.getOrElse(List.empty), _.name, "Event"),
      
      // Validate state machine references
      model.stateMachine.map(validateStateMachineReferences(_, commandNames, eventNames, stateNames))
        .getOrElse(().validNel),
        
      // Validate completion events references
      validateCompletionEventReferences(model.commands.getOrElse(List.empty), eventNames)
    )
    
    validations.sequence.map(_ => ())

  private def validateStateMachineReferences(
    stateMachine: StateMachine,
    commandNames: List[String],
    eventNames: List[String],
    stateNames: List[String]
  ): ValidationResult[Unit] =
    val validations = List(
      // Validate initial state exists
      Validators.validateReference(stateMachine.initialState, stateNames, "StateMachine", "State"),
      
      // Validate all transitions
      stateMachine.transitions.map(validateTransition(_, commandNames, eventNames, stateNames)).sequence
    )
    
    validations.sequence.map(_ => ())

  private def validateTransition(
    transition: Transition,
    commandNames: List[String],
    eventNames: List[String],
    stateNames: List[String]
  ): ValidationResult[Unit] =
    val validations = List(
      // Validate from/to states exist
      Validators.validateReference(transition.from, stateNames, "Transition", "State"),
      Validators.validateReference(transition.to, stateNames, "Transition", "State"),
      
      // Validate trigger references
      validateTrigger(transition.trigger, commandNames, eventNames)
    )
    
    validations.sequence.map(_ => ())

  private def validateTrigger(
    trigger: Trigger,
    commandNames: List[String],
    eventNames: List[String]
  ): ValidationResult[Unit] =
    val hasCommand = trigger.command.isDefined
    val hasEvent = trigger.event.isDefined
    val hasCondition = trigger.condition.isDefined
    
    if !hasCommand && !hasEvent && !hasCondition then
      ValidationError.MissingRequiredField("Trigger", "command or event or condition").invalidNel
    else
      val validations = List(
        trigger.command.map(cmd => 
          Validators.validateReference(cmd, commandNames, "Trigger", "Command")
        ).getOrElse(().validNel),
        
        trigger.event.map(evt => 
          Validators.validateReference(evt, eventNames, "Trigger", "Event")
        ).getOrElse(().validNel)
      )
      
      validations.sequence.map(_ => ())

  private def validateCompletionEventReferences(
    commands: List[Command],
    eventNames: List[String]
  ): ValidationResult[Unit] =
    val validations = commands.flatMap { command =>
      command.completionEvents.toList.flatMap { completionEvents =>
        List(
          completionEvents.success.map(evt =>
            Validators.validateReference(evt, eventNames, "CompletionEvents", "Event")
          ).getOrElse(().validNel),
          
          completionEvents.failure.map(evt =>
            Validators.validateReference(evt, eventNames, "CompletionEvents", "Event")
          ).getOrElse(().validNel)
        )
      }
    }
    
    validations.sequence.map(_ => ())

  private def validateStateMachine(model: HtcModel): ValidationResult[Unit] =
    model.stateMachine.map { stateMachine =>
      val stateNames = stateMachine.states.map(_.name)
      
      // Check for duplicate state names and validate initial state
      val validations = List(
        Validators.checkDuplicateNames(stateMachine.states, _.name, "State").map(_ => ()),
        Validators.validateReference(stateMachine.initialState, stateNames, "StateMachine", "State").map(_ => ()),
        validateStateReachability(stateMachine)
      )
      
      validations.sequence.map(_ => ())
      
    }.getOrElse(().validNel)

  private def validateStateReachability(stateMachine: StateMachine): ValidationResult[Unit] =
    val reachableStates = collection.mutable.Set(stateMachine.initialState)
    val allStates = stateMachine.states.map(_.name).toSet
    
    // Simple reachability check using fixed-point iteration
    var changed = true
    while changed do
      changed = false
      for transition <- stateMachine.transitions do
        if reachableStates.contains(transition.from) && !reachableStates.contains(transition.to) then
          reachableStates.add(transition.to)
          changed = true
    
    val unreachableStates = allStates -- reachableStates
    if unreachableStates.nonEmpty then
      ValidationError.InvalidStateTransition("", "", s"Unreachable states: ${unreachableStates.mkString(", ")}").invalidNel
    else
      ().validNel

  private def validateSemantics(model: HtcModel): ValidationResult[Unit] =
    // Additional semantic validations can be added here
    // For example: ensuring physics values are positive, AI model URIs are valid, etc.
    ().validNel

// Extension methods for easy validation
extension (model: HtcModel)
  def validate: ValidationResult[HtcModel] = summon[Validator[HtcModel]].validate(model)