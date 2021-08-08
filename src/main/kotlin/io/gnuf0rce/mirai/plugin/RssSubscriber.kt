package io.gnuf0rce.mirai.plugin

import com.rometools.rome.feed.synd.*
import io.gnuf0rce.mirai.plugin.data.*
import io.gnuf0rce.rss.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import net.mamoe.mirai.*
import net.mamoe.mirai.console.util.ContactUtils.getContact
import net.mamoe.mirai.console.util.ContactUtils.getContactOrNull
import net.mamoe.mirai.console.util.CoroutineScopeUtils.childScope
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import java.time.*
import kotlin.properties.*
import kotlin.reflect.*

object RssSubscriber : CoroutineScope by RssHelperPlugin.childScope("RssSubscriber") {
    private val histories by FeedRecordData::histories
    private val records by SubscribeRecordData::records
    private val mutex = Mutex()

    private var SyndEntry.history by object : ReadWriteProperty<SyndEntry, OffsetDateTime?> {
        override fun getValue(thisRef: SyndEntry, property: KProperty<*>): OffsetDateTime? {
            return histories[thisRef.uri]?.let(::timestamp)
        }

        override fun setValue(thisRef: SyndEntry, property: KProperty<*>, value: OffsetDateTime?) {
            histories[thisRef.uri] = value.orMinimum().toEpochSecond()
        }
    }

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

    private fun task(link: Url) = launch(SupervisorJob()) {
        while (isActive) {
            val record = mutex.withLock { records[link]?.takeIf { it.contacts.isNotEmpty() } } ?: return@launch
            delay(record.interval * 60 * 1000L)
            runCatching {
                client.feed(link)
            }.mapCatching { feed ->
                feed.entries.filter { it.history == null || it.last.orMinimum() > it.history.orMinimum() }.forEach { entry ->
                    logger.info { "${entry.uri}: ${entry.last.orMinimum()} ? ${entry.history}" }
                    record.sendFile { contact -> entry.toTorrent(contact) }
                    record.sendMessage { contact -> entry.toMessage(contact) }
                    entry.history = entry.last.orNow()
                }
            }.onFailure {
                logger.warning { "Rss: $link $it" }
            }
        }
    }

    suspend fun add(url: Url, subject: Contact) = mutex.withLock {
        val old = records[url] ?: SubscribeRecord()
        val new = if (old.contacts.isEmpty()) {
            val feed = client.feed(url)
            val now = OffsetDateTime.now()
            feed.entries.forEach { it.history = now }
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