package ca.danielpower.jstudy.core

import java.io.File

/**
 * Writes the stories the app bundles (its assets' stories/) for the
 * repository at the first argument into the directory at the second: the
 * path (path.json), each chapter's words and grammar (needs.json), and every
 * chapter and grammar page as read (read/, with the Japanese in grammar
 * pages' notes), and where each grammar topic is used (lines.json).
 */
fun main(args: Array<String>) {
    val out = File(args[1])
    out.deleteRecursively()
    File(out, "read").mkdirs()
    StoryTools.open(File(args[0])).use { tools ->
        File(out, "path.json").writeText(tools.pathJson)
        File(out, "needs.json").writeText(StoryPath.writeNeeds(tools.needs))
        for ((file, text) in StoryText.write(tools.text + tools.runs)) File(out, "read/$file").writeText(text)
        File(out, "lines.json").writeText(TopicLines.write(tools.lines))
    }
}
