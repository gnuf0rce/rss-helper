package io.gnuf0rce.mirai.plugin.data

import io.gnuf0rce.rss.*
import net.mamoe.mirai.console.data.PluginDataExtensions.mapKeys
import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value

object HttpClientConfig : ReadOnlyPluginConfig("HttpClientConfig"), RssHttpClientConfig {
    @ValueDescription("Dns Over Https Url")
    override val doh: String by value(DefaultDnsOverHttps)

    @ValueName("sni")
    @ValueDescription("SNI HostName Remove Regex")
    private val sni_: List<String> by value(DefaultSNIHosts.map { it.pattern })
    override val sni: List<Regex> by lazy { sni_.map { it.toRegex() } }

    @ValueDescription("MAP(host, proxy), default by host=127.0.0.1")
    override val proxy: Map<String, String> by value(DefaultProxy)

    @ValueDescription("DNS CNAME")
    override val cname: Map<Regex, List<String>> by value(DefaultCNAME.mapKeys { it.key.toString() })
        .mapKeys(::Regex, ""::plus)
}