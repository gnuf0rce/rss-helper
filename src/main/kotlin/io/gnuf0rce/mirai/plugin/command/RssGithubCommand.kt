package io.gnuf0rce.mirai.plugin.command

import io.gnuf0rce.mirai.plugin.*
import io.ktor.http.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*

object RssGithubCommand : CompositeCommand(
    owner = RssHelperPlugin,
    "rss-github",
    description = "Github Rss 订阅 系列 指令",
    overrideContext = RssCommandArgumentContext
) {

    private val Releases = { owner: String, repo: String -> Url("https://github.com/$owner/$repo/releases.atom") }

    private val Commits = { owner: String, repo: String -> Url("https://github.com/$owner/$repo/commits.atom") }

    private val Tags = { owner: String, repo: String -> Url("https://github.com/$owner/$repo/tags.atom") }

    private val Activity = { user: String -> Url("https://github.com/$user.atom") }

    private val Private = { user: String, secret: String -> Url("https://github.com/$user.private.atom?token=$secret") }

    @SubCommand
    @Description("添加一个Releases订阅")
    suspend fun CommandSenderOnMessage<*>.releases(owner: String, repo: String) = sendMessage {
        RssSubscriber.add(Releases(owner, repo), fromEvent.subject).let { (name, _, _) ->
            "Releases($owner/$repo)订阅任务[${name}]已添加".toPlainText()
        }
    }

    @SubCommand
    @Description("添加一个Commits订阅")
    suspend fun CommandSenderOnMessage<*>.commits(owner: String, repo: String) = sendMessage {
        RssSubscriber.add(Commits(owner, repo), fromEvent.subject).let { (name, _, _) ->
            "Commits($owner/$repo)订阅任务[${name}]已添加".toPlainText()
        }
    }

    @SubCommand
    @Description("添加一个Tags订阅")
    suspend fun CommandSenderOnMessage<*>.tags(owner: String, repo: String) = sendMessage {
        RssSubscriber.add(Tags(owner, repo), fromEvent.subject).let { (name, _, _) ->
            "Tags($owner/$repo)订阅任务[${name}]已添加".toPlainText()
        }
    }

    @SubCommand
    @Description("添加一个Activity订阅")
    suspend fun CommandSenderOnMessage<*>.activity(user: String) = sendMessage {
        RssSubscriber.add(Activity(user), fromEvent.subject).let { (name, _, _) ->
            "Activity($user)订阅任务[${name}]已添加".toPlainText()
        }
    }

    @SubCommand
    @Description("添加一个Private订阅")
    suspend fun CommandSenderOnMessage<*>.private(user: String, secret: String) = sendMessage {
        RssSubscriber.add(Private(user, secret), fromEvent.subject).let { (name, _, _) ->
            "Private($user)订阅任务[${name}]已添加".toPlainText()
        }
    }
}