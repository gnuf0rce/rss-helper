package io.gnuf0rce.mirai.plugin

import io.gnuf0rce.mirai.plugin.data.FeedRecordData
import io.gnuf0rce.mirai.plugin.data.SubscribeRecord
import io.gnuf0rce.mirai.plugin.data.SubscribeRecordData
import io.gnuf0rce.rss.*
import io.gnuf0rce.rss.feed
import io.gnuf0rce.rss.orMinimum
import io.gnuf0rce.rss.timestamp
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.util.ContactUtils.getContact
import net.mamoe.mirai.console.util.ContactUtils.getContactOrNull
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScope
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.FileSupported
import net.mamoe.mirai.message.data.FileMessage
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.utils.RemoteFile.Companion.sendFile
import net.mamoe.mirai.utils.RemoteFile.Companion.uploadFile
import java.io.File
import java.time.OffsetDateTime
import kotlin.time.minutes

object RssSubscriber : CoroutineScope by RssHelperPlugin.childScope("RssSubscriber") {
    private val histories by FeedRecordData::histories
    private val records by SubscribeRecordData::records
    private val mutex = Mutex()

    private fun last(uri: String) = histories[uri]?.let(::timestamp).orMinimum()

    private suspend fun SubscribeRecord.sendMessage(block: suspend (Contact) -> Message) {
        contacts.forEach { id ->
            runCatching {
                Bot.instances.first { it.getContactOrNull(id) != null }.getContact(id)
            }.onFailure {
                logger.warning("查找联系人${id}失败", it)
            }.mapCatching { contact ->
                contact.sendMessage(block(contact))
            }.onFailure {
                logger.warning("向${id}发送消息失败", it)
            }
        }
    }

    private suspend fun SubscribeRecord.sendFile(block: suspend (FileSupported) -> Message?) {
        contacts.forEach { id ->
            runCatching {
                Bot.instances.first { it.getContactOrNull(id) != null }.getContact(id)
            }.onFailure {
                logger.warning("查找联系人${id}失败", it)
            }.mapCatching { contact ->
                if (contact !is FileSupported) return@mapCatching
                contact.sendMessage(block(contact) ?: return@mapCatching)
            }.onFailure {
                logger.warning("向${id}发送文件失败", it)
            }
        }
    }

    private fun task(link: Url) = launch(Dispatchers.IO) {
        while (isActive) {
            val record = mutex.withLock { records[link]?.takeIf { it.contacts.isNotEmpty() } } ?: return@launch
            delay(record.interval.minutes)
            runCatching {
                feed(link)
            }.onSuccess { feed ->
                feed.entries.filter { it.last.orMinimum() >= last(it.uri) }.forEach { entry ->
                    record.sendFile { contact -> entry.toTorrent(contact) }
                    record.sendMessage { contact -> entry.toMessage(contact) }
                    histories[entry.uri] = entry.published.orNow().toEpochSecond()
                }
            }.onFailure {
                logger.warning("Rss: $link", it)
            }
        }
    }

    suspend fun add(url: Url, subject: Contact) = mutex.withLock {
        val old = records[url] ?: SubscribeRecord()
        val new = if (old.contacts.isEmpty()) {
            val feed = feed(url)
            val now = OffsetDateTime.now().toEpochSecond()
            feed.entries.forEach { histories[it.uri] = now }
            task(url)
            SubscribeRecord(contacts = setOf(subject.id), name = feed.title)
        } else {
            old.copy(contacts = old.contacts + subject.id)
        }
        records[url] = new
        new
    }

    suspend fun list(subject: Contact) = mutex.withLock {
        records.filter { (_, record) ->
            subject.id in record.contacts
        }
    }

    suspend fun interval(url: Url, duration: Int) = mutex.withLock {
        check(duration > 0) { "订阅时间需要正数" }
        val old = requireNotNull(records[url]) { "订阅不存在" }
        val new = old.copy(interval = duration)
        records[url] = new
        new
    }

    suspend fun stop(url: Url, subject: Contact) = mutex.withLock {
        val old = requireNotNull(records[url]) { "订阅不存在" }
        val new = old.copy(contacts = old.contacts - subject.id)
        records[url] = new
        new
    }

    fun start() {
        SubscribeRecordData.records.keys.forEach(::task)
    }

    fun stop() {
        coroutineContext.cancelChildren()
    }
}