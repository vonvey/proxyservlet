package io.bicycle.proxy;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * User: pschwarz
 * Date: 8/23/12
 * Time: 8:17 PM
 */
public class ProxyTargetTest {
    @Test
    public void proxyTargetShouldCreateASimpleBaseUrl() {
        assertThat(new ProxyTarget("bicycle.io").getProxyBaseURl(), is("http://bicycle.io/"));
    }

    @Test
    public void proxyTargetShouldCreateAComplexBaseURL() throws Exception {
        assertThat(new ProxyTarget("https", "bicycle.io", 8080, "/foo").getProxyBaseURl(), is("https://bicycle.io:8080/foo"));
    }

    @Test
    public void proxyTargetShouldCreateAStandardHttpsBaseUrl() throws Exception {
        assertThat(new ProxyTarget("https", "bicycle.io", 443, "").getProxyBaseURl(), is("https://bicycle.io/"));
    }
}
