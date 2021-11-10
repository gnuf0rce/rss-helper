package io.github.gnuf0rce.mirai.plugin.command

import io.github.gnuf0rce.mirai.plugin.*
import io.github.gnuf0rce.mirai.plugin.data.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.*

object RssHubCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-hub", "rsshub",
    description = "RssHub 订阅 指令",
    overrideContext = RssCommandArgumentContext
) {

    private val domain by RssHubConfig::domain

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun routes(): RssHubRoutes {
        return Json.decodeFromString(client.useHttpClient { it.get("https://${domain}/api/routes") })
    }

    @SubCommand
    @Description("添加一个RssHub订阅")
    suspend fun CommandSenderOnMessage<*>.add() = sendMessage {
        val routes = routes()
        sendMessage(routes.message)
        sendMessage("请输入路由名")
        val key = fromEvent.nextMessage().content.trim()
        val route = requireNotNull(routes.data[key]) { "未找到路由${key}" }
        sendMessage(route.routes.mapIndexed { index, s -> index to s }.joinToString("\n"))
        val link = if (route.routes.size > 1) {
            sendMessage("选择路由（输入序号）")
            route.routes[fromEvent.nextMessage().content.trim().toInt()]
        } else {
            route.routes.single()
        }
        var stop = false
        val path = link.split("/").mapNotNull {
            if (stop) return@mapNotNull null
            if (it.startsWith(":")) {
                lateinit var value: String
                while (isActive) {
                    sendMessage("${link}, 输入${it} (置空可以输入#)")
                    value = fromEvent.nextMessage().content.trim()
                    if (value.isBlank() || value.startsWith("#")) {
                        if (it.endsWith("?")) {
                            stop = true
                            return@mapNotNull null
                        } else {
                            sendMessage("${it}不能为空")
                            continue
                        }
                    } else {
                        break
                    }
                }
                value
            } else {
                it
            }
        }.joinToString(separator = "/", prefix = "")

        val (name) = RssSubscriber.add(Url("https://${domain}${path}"), fromEvent.subject)
        "RSS订阅任务[${name}]已添加".toPlainText()
    }
}