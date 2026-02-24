# Repository Guidelines

## Project Structure & Module Organization
This is a Spring Boot 3 (Java 21) backend API.
- `src/main/java/com/wing/backendapiexpensespringboot`: application code
- `controller/`: REST endpoints (`AiChatController`, `AiForecastController`, etc.)
- `service/`: business logic and orchestration (`AiOrchestratorService`, domain services)
- `repository/`: Spring Data JPA repositories
- `model/`: JPA entities
- `dto/`: request/response contracts
- `config/`, `security/`, `exception/`: cross-cutting setup and error handling
- `src/main/resources`: `application.properties`, profile configs, static/templates
- `src/test/java`: tests (currently Spring context smoke test)

## Build, Test, and Development Commands
Use Maven Wrapper so contributors do not need a global Maven install.
- Windows run: `.\mvnw.cmd spring-boot:run`
- macOS/Linux run: `./mvnw spring-boot:run`
- Test suite: `.\mvnw.cmd test`
- Full verification: `.\mvnw.cmd clean verify`
- Package artifact: `.\mvnw.cmd clean package`

Use `SPRING_PROFILES_ACTIVE=prod` (or `dev`) to switch profile-specific settings.

## Coding Style & Naming Conventions
- Java style: 4-space indentation, UTF-8, one public class per file.
- Package names are lowercase; classes use `UpperCamelCase`; methods/fields use `lowerCamelCase`.
- Keep layer-specific suffixes: `*Controller`, `*Service`, `*Repository`, `*Entity`, `*Request`, `*Response`.
- Prefer constructor injection (often via Lombok `@RequiredArgsConstructor`).
- Use structured logging (`@Slf4j`); avoid `System.out.println`.

## Testing Guidelines
- Frameworks: JUnit 5 + `spring-boot-starter-test`.
- Put tests under mirrored package paths in `src/test/java`.
- Name test classes `*Tests` (example: `BackendApiExpenseSpringbootApplicationTests`).
- Add focused unit/service tests for business logic and integration tests for endpoint + persistence flows.
- Run tests locally before pushing: `.\mvnw.cmd test`.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commits (examples: `feat(service): ...`, `test(core): ...`).
- Commit format: `type(scope): concise summary`
- Preferred types: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`.
- PRs should include: purpose, linked issue, test evidence, and any API/config changes.
- For endpoint changes, include sample request/response payloads.

## Security & Configuration Tips
- Never commit secrets. Use environment variables for DB, Firebase, and OpenRouter credentials.
- Keep production CORS restrictive and validate profile-specific config before release.
- Treat `application-dev.properties` as local convenience, not a source of production credentials.
