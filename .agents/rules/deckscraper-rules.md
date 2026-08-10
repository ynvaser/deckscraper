# Deckscraper Project Rules & Guidelines

This directory contains workspace-level rules for Antigravity AI agents operating on the **Deckscraper** project (`systems.bdev.deckscraper`).

---

## 1. Java & Spring Boot Coding Standards

- **Target SDK**: Java 17 (`sourceCompatibility = '17'`). Utilize Java 17 language features (records, text blocks, pattern matching, switch expressions) where clean and appropriate.
- **Framework & Libraries**:
  - **Spring Boot**: Version `2.7.1` (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`).
  - **Lombok**: Use `@Slf4j` for logging, `@Data`, `@Value`, `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor` to reduce boilerplate. Avoid manual getter/setter declarations when Lombok annotations are suitable.
  - **JSON & Data Processing**: Use Jackson databind modules (`jackson-datatype-jsr310`) and Apache Commons (`commons-csv`, `commons-lang3`).
  - **Persistence**: H2 Database (`com.h2database:h2`) and Spring Data JPA repositories.
- **Logging**: Use SLF4J logger (`log.info()`, `log.error()`, `log.warn()`, `log.debug()`) provided by `@Slf4j`. Do not use `System.out.println()` or `e.printStackTrace()`.
- **Package Hierarchy**: Respect existing package organization under `systems.bdev.deckscraper`:
  - `config`: Spring Configuration beans (`BeanConfig.java`)
  - `input`: Data fetching & parsing services (`CsvParserService`, `EdhRecDeckScraper`, `ScryfallService`, `CubeCobraService`)
  - `model`: Domain and data models (`Card`, `Deck`, `Cube`, `AverageDeck`, `CardType`, `Cardholder`)
  - `persistence`: JPA Entities, repositories, and custom converters (`DeckEntity`, `CubeEntity`, `ConfigEntity`)
  - `service`: Core business logic (`DeckScraperService`, `DeckSaverService`)
  - `util`: Serializers, deserializers, and helper functions (`CardSerializer`, `CardDeserializer`, `Utils`)

---

## 2. Compilation, Building & Testing Strategy (IntelliJ MCP First)

When compiling code or executing tests, agents MUST attempt to use the **IntelliJ MCP tools** first. Fall back to Gradle wrapper CLI commands only if the IntelliJ MCP tools are unavailable, disconnected, or fail.

### A. Compilation & Building
1. **Primary (IntelliJ MCP)**:
   - Call `build_project` with `projectPath` set to the project root.
   - For fast incremental checks, pass specific modified files in `filesToRebuild`.
   - Set `rebuild: true` if a full clean build is required.
2. **Fallback (Terminal / Gradle CLI)**:
   - `.\gradlew.bat compileJava` or `.\gradlew.bat build`

### B. Running Tests
1. **Primary (IntelliJ MCP)**:
   - Use `get_run_configurations` to discover existing test run configurations or locate test entry points (`filePath`).
   - Use `execute_run_configuration` passing `configurationName` or `filePath` + `line` to execute unit/integration tests directly within the IDE.
2. **Fallback (Terminal / Gradle CLI)**:
   - `.\gradlew.bat test` (or `.\gradlew.bat test --tests <TestClassName>`)

### C. Assembly & Distribution Packaging
1. **Primary (IntelliJ MCP)**:
   - Run via existing Gradle run configuration in IntelliJ using `execute_run_configuration`.
2. **Fallback (Terminal / Gradle CLI)**:
   - `.\gradlew.bat assemble` (Triggers DB & script copy tasks and `packageDistribution` ZIP creation).

---

## 3. Agent Workflow & Safety Guidelines

- **Preserve Documentation & Comments**: Maintain existing Javadoc comments, method contracts, and inline documentation unless explicitly instructed to revise them.
- **Empirical Verification**:
  - **NEVER** declare success on a task, bugfix, or refactor without running the appropriate build or test verification (preferably via IntelliJ MCP `build_project` / `execute_run_configuration`, or CLI fallback).
- **No Superficial Fixes**:
  - Do not swallow exceptions with empty `catch` blocks or suppress error output without logging.
  - Do not alter existing unit tests or assertions simply to make builds pass without addressing the root cause.
- **No Parallel Execution of Gradle / IDE Tasks**:
  - **NEVER** run Gradle CLI commands or IntelliJ MCP tasks that trigger Gradle (e.g. `execute_run_configuration` for Gradle run configurations, `build_project`, `execute_terminal_command`, or `run_command` running `gradlew`) in parallel.
  - Always execute build and test tasks sequentially, waiting for one process to finish completely before launching another.
- **Preserve API & Method Signatures**: When modifying methods or services, ensure all invocation sites across the codebase are updated to prevent compilation or runtime breakage.
- **Error Diagnosis**: Base bug diagnoses strictly on un-truncated logs and stack traces extracted directly from IntelliJ MCP output or terminal execution output.

