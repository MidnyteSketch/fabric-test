# Remote build (no local development setup required)

This project includes `.github/workflows/build.yml`.

When uploaded to a GitHub repository, GitHub Actions will install Java 25 and Gradle 9.4.1 on GitHub's servers, download the Fabric/Minecraft dependencies there, and run `gradle clean build`.

The resulting JARs are uploaded as the `patches-26.2-test1-build` workflow artifact. If the build fails, the Actions log contains the compiler errors needed for the next source revision.
