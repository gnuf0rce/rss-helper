# [Rss Helper](https://github.com/gnuf0rce/rss-helper)

> 基于 [Mirai Console](https://github.com/mamoe/mirai-console) 的RSS订阅插件

[![Release](https://img.shields.io/github/v/release/gnuf0rce/rss-helper)](https://github.com/gnuf0rce/rss-helper/releases)
[![Release](https://img.shields.io/github/downloads/gnuf0rce/rss-helper/total)](https://shields.io/category/downloads)

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

## TODO

- [ ] 翻译功能
- [ ] 代理支持
- [x] Doh支持
- [ ] 将Html转化为Mirai的MessageChain(主要目的是显示图片)
  
