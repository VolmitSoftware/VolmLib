val configuredGroup: String = providers.gradleProperty("group")
    .orElse("art.arcane")
    .get()
val configuredVersion: String = providers.gradleProperty("version")
    .orElse("local-SNAPSHOT")
    .get()

group = configuredGroup
version = configuredVersion

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
