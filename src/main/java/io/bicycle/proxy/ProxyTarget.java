package io.bicycle.proxy;

import com.google.common.base.Objects;

/**
 * User: pschwarz
 * Date: 8/23/12
 * Time: 7:55 PM
 */
public class ProxyTarget {


    private final String proxyProtocol;

    private final String proxyHost;
    private final int proxyPort;
    private final String proxyPath;

    public ProxyTarget(final String proxyHost) {
        this("http", proxyHost, 80, "");
    }

    public ProxyTarget(final String proxyProtocol, final String proxyHost, final int proxyPort, final String proxyPath) {
        this.proxyProtocol = proxyProtocol;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyPath = proxyPath.startsWith("/") ? proxyPath : "/" + proxyPath;
    }

    public String getProxyProtocol() {
        return proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String getProxyPath() {
        return proxyPath;
    }

    public String getProxyHostAndPort() {
        if (this.getProxyPort() == 80 || this.getProxyPort() == 443) {
            return this.getProxyHost();
        } else {
            return this.getProxyHost() + ":" + this.getProxyPort();
        }
    }

    public String getProxyBaseURl() {
        return this.proxyProtocol + "://" + this.getProxyHostAndPort() + getProxyPath();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("proxyHost", proxyHost)
                .add("proxyPort", proxyPort)
                .toString();
    }

}
