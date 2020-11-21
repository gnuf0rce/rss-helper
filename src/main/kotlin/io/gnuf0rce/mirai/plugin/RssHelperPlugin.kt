package io.gnuf0rce.mirai.plugin

import com.google.auto.service.AutoService
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.utils.minutesToMillis

@AutoService(JvmPlugin::class)
object RssHelperPlugin : KotlinPlugin(
    JvmPluginDescription("io.github.gnuf0rce.rss-helper", "0.1.0-dev-1") {
        name("rss-helper")
        author("cssxsh")
    }
) {
    @ConsoleExperimentalApi
    override val autoSaveIntervalMillis: LongRange
        get() = 3.minutesToMillis..10.minutesToMillis

    @ConsoleExperimentalApi
    override fun onEnable() {
        //
    }

    @ConsoleExperimentalApi
    override fun onDisable() {
        //
    }
}