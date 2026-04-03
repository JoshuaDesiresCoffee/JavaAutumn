# Java Autumn 

Best-in-class Java web framework.  
A lightweight and flexible alternative to heavy frameworks like Spring, inspired by minimal Go frameworks such as Chi.

Java Autumn provides routing, server-side rendering, and database access out of the box — with minimal setup and no magic.

---

##  Features


.....

......







##  Quick Start (Java 25)
- Java 25 installed (java -version, javac -version)
- SQLite JDBC jar:
- Autumn.lib/sqlite-jdbc-3.51.3.0.jar

Build
-    .\build.ps1

Run
-  Build + start:
-    .\run.ps1

Skip build:
-  .\run.ps1 -SkipBuild

Clean build:
-    .\build.ps1 -Clean

Tests
-  Uses Java’s built-in assert (enable with -ea).
-    .\test.ps1

Skip rebuild:
-    .\test.ps1 -SkipBuild

Manual execution:
-    java -ea -cp "out;Autumn.lib/sqlite-jdbc-3.51.3.0.jar" Implementation.tests.TestRunner






