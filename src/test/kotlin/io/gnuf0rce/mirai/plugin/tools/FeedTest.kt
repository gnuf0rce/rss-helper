package io.gnuf0rce.mirai.plugin.tools

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class FeedTest {
    @Test
    fun feedUrl(): Unit = runBlocking {
        Feed.feedUrl("https://rsshub.app/github/repos/DIYgod").entries.forEach {
            println(it.title)
        }
    }
}