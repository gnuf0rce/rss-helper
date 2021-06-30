# [Rss Helper](https://github.com/gnuf0rce/rss-helper)

> 基于 [Mirai Console](https://github.com/mamoe/mirai-console) 的RSS订阅插件

[![Release](https://img.shields.io/github/v/release/gnuf0rce/rss-helper)](https://github.com/gnuf0rce/rss-helper/releases)
[![Downloads](https://img.shields.io/github/downloads/gnuf0rce/rss-helper/total)](https://shields.io/category/downloads)
[![MiraiForum](https://img.shields.io/badge/post-on%20MiraiForum-yellow)](https://mirai.mamoe.net/topic/334)

## 指令

注意: 使用前请确保可以 [在聊天环境执行指令](https://github.com/project-mirai/chat-command)  
带括号的`/`前缀是可选的  
`<...>`中的是指令名，由空格隔开表示或，选择其中任一名称都可执行例如`/rss add https://github.com/cssxsh.atom`  
`[...]`表示参数，当`[...]`后面带`?`时表示参数可选  
`{...}`表示连续的多个参数 下列指令的 URL 参数可以使用 base64 编码 后的形式

### RssBaseCommand

| 指令                                      | 描述                   |
|:------------------------------------------|:-----------------------|
| `/<rss 公招> <add> [url]`                 | 添加一个订阅           |
| `/<rss 公招> <list>`                      | 列出订阅列表           |
| `/<rss 公招> <interval> [url] [duration]` | 设置订阅间隔, 单位分钟 |
| `/<rss 公招> <stop> [url]`                | 取消一个订阅           |

### RssGithubCommand

| 指令                                      | 描述                 |
|:------------------------------------------|:---------------------|
| `/<rss-github> <releases> [owner] [repo]` | 添加一个Releases订阅 |
| `/<rss-github> <commits> [owner] [repo]`  | 添加一个Commits订阅  |
| `/<rss-github> <tags> [owner] [repo]`     | 添加一个Tags订阅     |
| `/<rss-github> <activity> [user]`         | 添加一个Activity订阅 |
| `/<rss-github> <private> [user] [secret]` | 添加一个Private订阅  |

### RssMikanCommand

| 指令                                       | 描述                  |
|:-------------------------------------------|:----------------------|
| `/<rss-mikan mikan> <my> [token]`          | 添加一个MyBangumi订阅 |
| `/<rss-mikan mikan> <classic>`             | 添加一个Classic订阅   |
| `/<rss-mikan mikan> <bangumi> [id] [sub]?` | 添加一个Bangumi订阅   |
| `/<rss-mikan mikan> <search> [word]`       | 添加一个Search订阅    |

### RssTestCommand

| 指令                        | 描述         |
|:----------------------------|:-------------|
| `/<rss-test> <build> [url]` | 测试一个订阅 |
| `/<rss-test> <clear>`       | 清空种子文件 |

### RssMiraiCommand

| 指令                                    | 描述                 |
|:----------------------------------------|:---------------------|
| `/<rss-mirai mirai> <category> [value]` | 添加标签订阅         |
| `/<rss-mirai mirai> <plugin>`           | 添加插件发布订阅     |
| `/<rss-mirai mirai> <other>`            | 添加其他项目发布订阅 |

### RssHubCommand

| 指令                      | 描述               |
|:--------------------------|:-------------------|
| `/<rss-hub rsshub> <add>` | 交互添加RssHub订阅 |

交互过程举例
```
1748(1438159989)  23:29:58
/rsshub add

QQBot(3337342367)  23:30:02
request returned 1899 routes

QQBot(3337342367)  23:30:03
请输入路由名

1748(1438159989)  23:30:07
755

QQBot(3337342367)  23:30:07
(0, /755/user/:username)

QQBot(3337342367)  23:30:08
/755/user/:username, 输入:username (置空可以输入#)

1748(1438159989)  23:30:21
akimoto-manatsu

QQBot(3337342367)  23:30:26
1748  
/rsshub add
RSS订阅任务[秋元真夏(乃木坂46) - 755]已添加
```

配置文件 `RssHubConfig.yml` 可以配置rsshub的域名，即可以配置rsshub的源

## TODO

- [ ] 翻译功能
- [ ] 代理支持
- [x] Doh支持
- [x] 将Html转化为Mirai的MessageChain(主要目的是显示图片)
  
