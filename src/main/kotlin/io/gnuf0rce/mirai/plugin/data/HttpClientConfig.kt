package io.gnuf0rce.mirai.plugin.data

import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object HttpClientConfig: ReadOnlyPluginConfig("HttpClientConfig") {
    @ValueDescription("Dns Over Https Url")
    val doh: String by value("https://public.dns.iij.jp/dns-query")

    @ValueDescription("SNI HostName Remove Regex")
    val sni: List<String> by value(listOf("""sukebei\.nyaa\.(si|net)"""))

    @ValueDescription("MAP(host, proxy), default by host=127.0.0.1")
    val proxy: MutableMap<String, String> by value(mutableMapOf(
        "www.google.com" to "http://127.0.0.1:8080",
        "twitter.com" to "socks://127.0.0.1:1080"
    ))
}