package io.github.gnuf0rce.rss

public class HostConnectException(host: String, override val cause: Throwable) :
    IllegalStateException("Host: $host")