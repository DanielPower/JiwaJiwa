package ca.danielpower.jstudy.core

import java.io.File

/**
 * Writes every chapter and grammar page as read (StoryText) for the
 * repository at the first argument into the directory at the second, which
 * the app build bundles as stories/read.
 */
fun main(args: Array<String>) {
    val out = File(args[1])
    out.deleteRecursively()
    out.mkdirs()
    StoryTools.open(File(args[0])).use { tools ->
        for ((file, text) in StoryText.write(tools.text)) File(out, file).writeText(text)
    }
}
