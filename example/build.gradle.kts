plugins { application }
dependencies { implementation(project(":")) }
val targetJava = providers.gradleProperty("testJavaVersion").getOrElse(JavaVersion.current().majorVersion).toInt()
java { toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17); options.encoding = "UTF-8" }
application { mainClass.set("example.FileStreamsApplication") }

val transportFixture = sourceSets.create("transportFixture") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[transportFixture.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[transportFixture.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

tasks.register<JavaExec>("runTransportFixture") {
    description = "Run bounded test-only memory storage through the real Boot HTTP/2 TLS endpoint"
    group = "verification"
    classpath = transportFixture.runtimeClasspath
    mainClass.set("example.TransportFixture")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(targetJava)) })
    maxHeapSize = "256m"
}
