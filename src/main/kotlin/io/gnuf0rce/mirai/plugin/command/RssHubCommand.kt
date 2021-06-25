package io.gnuf0rce.mirai.plugin.command

import io.gnuf0rce.mirai.plugin.RssHelperPlugin
import io.gnuf0rce.mirai.plugin.RssSubscriber
import io.gnuf0rce.mirai.plugin.data.RssHubConfig
import io.gnuf0rce.mirai.plugin.data.RssHubRoutes
import io.gnuf0rce.rss.useHttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.isActive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.data.toPlainText
import net.mamoe.mirai.message.nextMessage

object RssHubCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-hub", "rsshub",
    description = "RssHub 订阅 指令",
    overrideContext = RssCommandArgumentContext
) {

    private val domain by RssHubConfig::domain

    private suspend fun routes(): RssHubRoutes {
        return Json.decodeFromString(useHttpClient { it.get("https://${domain}/api/routes") })
    }

    @SubCommand
    @Description("添加一个RssHub订阅")
    suspend fun CommandSenderOnMessage<*>.add() = sendMessage {
        val routes = routes()
        sendMessage(routes.message)
        sendMessage("请输入路由名")
        val name = fromEvent.nextMessage().content.trim()
        val route = requireNotNull(routes.data[name]) { "未找到路由${name}" }
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

        RssSubscriber.add(Url("https://${domain}${path}"), fromEvent.subject).let { (name, _, _) ->
            "RSS订阅任务[${name}]已添加".toPlainText()
        }
    }
}