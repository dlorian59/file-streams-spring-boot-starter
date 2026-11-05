plugins {
    `java-library`
    `maven-publish`
}
group = "io.github.59alex"
version = "0.1.0"
allprojects {
    repositories {
        maven { url = uri(rootProject.file("../file-streams/build/local-repository")) }
        mavenCentral()
    }
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(
        providers.gradleProperty("testJavaVersion").getOrElse(JavaVersion.current().majorVersion).toInt()))
    withSourcesJar()
    withJavadocJar()
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17); options.encoding = "UTF-8" }
tasks.withType<Test>().configureEach { useJUnitPlatform(); maxHeapSize = "256m" }
tasks.withType<Jar>().configureEach { from("LICENSE") { into("META-INF") } }
dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    api("org.springframework.boot:spring-boot-starter-webflux")
    api("io.github.59alex:file-streams-s3:0.1.0")
    api("io.github.59alex:file-streams-webflux:0.1.0")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:4.1.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            pom {
                name.set("File Streams Spring Boot Starter")
                description.set("Opt-in Spring Boot integration for continuous file streams")
                url.set("https://github.com/dlorian59/file-streams-spring-boot-starter")
                licenses { license { name.set("MIT License"); url.set("https://opensource.org/license/mit") } }
                developers { developer { id.set("dlorian59"); name.set("Aleksandr Kashevarov") } }
                scm { url.set("https://github.com/dlorian59/file-streams-spring-boot-starter") }
            }
        }
    }
}
