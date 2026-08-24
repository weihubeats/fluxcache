package com.fluxcache.admin.config;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Dashboard token 鉴权回归：无鉴权曾导致任意网络访问者可清空生产缓存。
 *
 * @author : wh
 */
public class FluxCacheTokenFilterTest {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @Before
    public void setUp() {
        request = new MockHttpServletRequest("POST", "/cache/manager/v1/clear");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    public void validToken_passesThrough() throws Exception {
        FluxCacheTokenFilter filter = new FluxCacheTokenFilter("secret");

        request.addHeader(FluxCacheTokenFilter.TOKEN_HEADER, "secret");
        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    @Test
    public void missingToken_rejected401() throws Exception {
        FluxCacheTokenFilter filter = new FluxCacheTokenFilter("secret");

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(chain.getRequest() == null);
    }

    @Test
    public void wrongToken_rejected401() throws Exception {
        FluxCacheTokenFilter filter = new FluxCacheTokenFilter("secret");

        request.addHeader(FluxCacheTokenFilter.TOKEN_HEADER, "guess");
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(chain.getRequest() == null);
    }

    @Test
    public void blankToken_noAuthCheck() throws Exception {
        FluxCacheTokenFilter filter = new FluxCacheTokenFilter("");

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }
}
