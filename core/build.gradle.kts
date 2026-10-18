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
// The tools share the reader's internals (its tokens and grammar patterns) with the app's code.
kotlin.target.compilations.getByName("stories").associateWith(kotlin.target.compilations.getByName("main"))
kotlin.target.compilations.getByName("test").associateWith(kotlin.target.compilations.getByName("stories"))

dependencies {
    // JSON for the bundled stories file.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    // The story tools read the stories with a Japanese tokenizer (IPADIC dictionary bundled in the jar)
    // and the built dictionary, and StoryPathTest checks the stories with them. The app ships what they read.
    "storiesImplementation"("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    "storiesImplementation"("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation(stories.output)
    testImplementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
}

tasks.register<JavaExec>("stories") {
    group = "stories"
    description = "Writes the app's stories (path.json) from tools/stories, their index (needs.json), each story's SUMMARY.md and COVERAGE.md, and says what's wrong with them (-Pchapter=story/slug for one chapter). Needs the dictionary built."
    classpath = stories.runtimeClasspath
    mainClass.set("ca.danielpower.jstudy.core.StoryToolsKt")
    // -Pchapter=story/slug (or the chapter's file) says all about that chapter.
    args(rootDir, findProperty("chapter")?.toString().orEmpty())
}

// Every chapter and grammar page split into words, which the app bundles as
// stories/read (app/build.gradle.kts). Written for each build rather than
// committed. Needs the dictionary built.
tasks.register<JavaExec>("storyText") {
    group = "stories"
    description = "Writes the stories as the app shows them (stories/read). Needs the dictionary built."
    classpath = stories.runtimeClasspath
    mainClass.set("ca.danielpower.jstudy.core.StoryTextToolKt")
    val out = layout.buildDirectory.dir("generated/storyText")
    inputs.files(fileTree("../tools/stories") { exclude("**/*.md") }).withPropertyName("storySources")
    inputs.file("../app/src/main/assets/databases/dictionary.db").withPropertyName("dictionary")
    outputs.dir(out).withPropertyName("text")
    args(rootDir, out.get().asFile)
}

tasks.test {
    useJUnitPlatform()
    // StoryPathTest reads the app's bundled stories, their index and summaries, and, when it's been built, the dictionary.
    inputs.file("../app/src/main/assets/stories/path.json").withPropertyName("stories")
    inputs.file("../app/src/main/assets/stories/needs.json").withPropertyName("storyNeeds")
    inputs.files(fileTree("../tools/stories")).withPropertyName("storySources")
    inputs.files(fileTree("../app/src/main/assets/databases") { include("dictionary.db") }).withPropertyName("dictionary")
}
