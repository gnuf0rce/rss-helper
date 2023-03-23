package io.github.gnuf0rce.rss

import io.ktor.client.plugins.*

public class CloudflareException(override val cause: ResponseException) :
    IllegalStateException("Need Cloudflare CAPTCHA")