package io.gnuf0rce.mirai.plugin.tools

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.time.ZoneId
import java.util.*

internal class FeedToolTest {

    private fun Date.toOffsetFormat() =
        toInstant().atZone(ZoneId.systemDefault()).format(ISO_OFFSET_DATE_TIME)

    private val cacheDir = File("test")

    @Test
    fun feedUrl(): Unit = runBlocking {
        val timestamp = FeedTool.getFeedXml("https://github.com/cssxsh.atom", cacheDir)
        FeedTool.parseFeedXml(timestamp, cacheDir).entries.forEach {
            println("====================================================")
            println("TITLE: ${it.title}")
            println("AUTHOR: ${it.author}")
            println("PUBLISHED_DATE: ${it.publishedDate.toOffsetFormat()}")
            println("UPDATED_DATE: ${it.updatedDate.toOffsetFormat()}")
            println("LINK: ${it.link}")
            println("URI: ${it.uri}")
        }
    }
}