package com.htc.dtl.debug

object DtmiTest:
  def main(args: Array[String]): Unit =
    val dtmi = "dtmi:htc:mobility:car:1.0.0"
    val pattern = """^dtmi:[a-zA-Z][a-zA-Z0-9_]*(?::[a-zA-Z][a-zA-Z0-9_]*)*;[1-9][0-9]*(?:\.[0-9]+)*$""".r
    
    println(s"Testing DTMI: $dtmi")
    println(s"Pattern: ${pattern.pattern}")
    println(s"Matches: ${pattern.findFirstIn(dtmi).isDefined}")
    
    // Test each part
    val parts = dtmi.split(";")
    println(s"Before semicolon: ${parts(0)}")
    println(s"After semicolon: ${parts(1)}")
    
    val namePattern = """^dtmi:[a-zA-Z][a-zA-Z0-9_]*(?::[a-zA-Z][a-zA-Z0-9_]*)*$""".r
    println(s"Name part matches: ${namePattern.findFirstIn(parts(0)).isDefined}")
    
    val versionPattern = """^[1-9][0-9]*(?:\.[0-9]+)*$""".r
    println(s"Version part matches: ${versionPattern.findFirstIn(parts(1)).isDefined}")