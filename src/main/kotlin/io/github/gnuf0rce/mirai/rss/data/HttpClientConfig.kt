package io.github.gnuf0rce.mirai.rss.data

import io.github.gnuf0rce.rss.*
import kotlinx.serialization.modules.*
import net.mamoe.mirai.console.data.*

@PublishedApi
internal object HttpClientConfig : ReadOnlyPluginConfig("HttpClientConfig"), RssHttpClientConfig {
    override val serializersModule: SerializersModule = SerializersModule {
        contextual(RegexSerializer)
    }

    @ValueDescription("Dns Over Https Url")
    override val doh: String by value(DefaultDnsOverHttps)

    @ValueDescription("Use IPv6")
    override val ipv6: Boolean by value(false)

    @ValueName("sni")
    @ValueDescription("SNI HostName Remove Regex")
    override val sni: List<Regex> by value(DefaultSNIHosts)

    @ValueDescription("Http Timeout")
    override val timeout: Long by value(DefaultTimeout)

    @ValueDescription("MAP(host, proxy), default by host=127.0.0.1")
    override val proxy: Map<String, String> by value(DefaultProxy)

    @ValueDescription("DNS CNAME")
    override val cname: Map<Regex, List<String>> by value(DefaultCNAME)
}