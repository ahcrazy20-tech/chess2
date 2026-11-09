# Gradle Wrapper

This project uses Gradle 8.10.

`gradle-wrapper.jar` is intentionally not committed (to keep the repo light / avoid binary).

- **Android Studio**: Open `android/` — Studio generates the jar automatically.
- **CLI**: `gradle wrapper --gradle-version 8.10` (requires a local Gradle install) or download:
  ```
  curl -L https://github.com/gradle/gradle/raw/v8.10.0/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
  ```
CI uses `gradle/actions/setup-gradle@v4` which doesn’t need the jar.

