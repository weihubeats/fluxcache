package com.fluxcache.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin dashboard access control.
 *
 * @author : wh
 */
@Data
@ConfigurationProperties(prefix = "flux.cache.admin")
public class FluxCacheAdminProperties {

    /**
     * 是否启用 Dashboard REST 接口。包含清空/驱逐缓存等破坏性操作，
     * 生产环境建议配合 token 或网关鉴权使用。
     */
    private boolean enabled = true;

    /**
     * 访问令牌。非空时所有请求必须携带同名请求头，否则返回 401。
     * 留空表示不校验（仅建议内网/本地开发使用）。
     */
    private String token = "";
}
