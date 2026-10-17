plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// The story tools (src/stories): they read the bundled stories with the built
// dictionary and write what's worked out from them next to the stories. Not
// part of the app. See tools/stories/AGENTS.md.
val stories: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[stories.implementationConfigurationName].extendsFrom(configurations.implementation.get())

dependencies {
    // Japanese tokenizer for reading stories (IPADIC dictionary bundled in the jar).
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    // JSON for the bundled stories file.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    // The story tools read the built dictionary, and StoryPathTest checks the stories with them.
    "storiesImplementation"("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation(stories.output)
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
}

tasks.register<JavaExec>("stories") {
    group = "stories"
    description = "Writes the app's stories (path.json) from tools/stories, their index (needs.json) and each story's SUMMARY.md. Needs the dictionary built."
    classpath = stories.runtimeClasspath
    mainClass.set("ca.danielpower.jstudy.core.StoryToolsKt")
    args(rootDir)
}

tasks.test {
    useJUnitPlatform()
    // StoryPathTest reads the app's bundled stories, their index and summaries, and, when it's been built, the dictionary.
    inputs.file("../app/src/main/assets/stories/path.json").withPropertyName("stories")
    inputs.file("../app/src/main/assets/stories/needs.json").withPropertyName("storyNeeds")
    inputs.files(fileTree("../tools/stories")).withPropertyName("storySources")
    inputs.files(fileTree("../app/src/main/assets/databases") { include("dictionary.db") }).withPropertyName("dictionary")
}
