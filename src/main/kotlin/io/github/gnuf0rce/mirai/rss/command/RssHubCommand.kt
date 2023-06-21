package io.github.gnuf0rce.mirai.rss.command

import io.github.gnuf0rce.mirai.rss.*
import io.github.gnuf0rce.mirai.rss.data.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.*

@PublishedApi
internal object RssHubCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-hub", "rsshub",
    description = "RssHub 订阅 指令",
    overrideContext = RssCommandArgumentContext
) {

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun routes(): RssHubRoutes {
        return client.useHttpClient { http ->
            http.get {
                url {
                    takeFrom(RssHubConfig.host)
                    encodedPath = "api/routes"
                }
            }.body()
        }
    }

    @SubCommand
    @Description("添加一个RssHub订阅")
    suspend fun CommandSenderOnMessage<*>.add() = quote {
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
        val paths = link.split("/").mapNotNull {
            if (stop) return@mapNotNull null
            if (it.startsWith(":").not()) return@mapNotNull it
            while (isActive) {
                sendMessage("${link}, 输入${it} (置空可以输入#)")
                val value = fromEvent.nextMessage().content.trim()
                if (value.isBlank() || value.startsWith("#")) {
                    if (it.endsWith("?")) {
                        stop = true
                        return@mapNotNull null
                    } else {
                        sendMessage("${it}不能为空")
                        continue
                    }
                } else {
                    return@mapNotNull value
                }
            }
            null
        }

        val url = URLBuilder(RssHubConfig.host)
            .appendPathSegments(paths)
            .build()
        val record = RssSubscriber.add(url, fromEvent.subject)
        "RSS订阅任务[${record.name}]已添加".toPlainText()
    }
}