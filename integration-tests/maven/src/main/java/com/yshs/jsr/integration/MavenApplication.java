package com.yshs.jsr.integration;

import cn.hutool.core.util.IdUtil;

/**
 * 运行 Maven 集成测试项目，并确认 Hutool 依赖可用。
 */
public final class MavenApplication {

    /**
     * 输出由 Hutool 生成的 UUID。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("UUID=" + IdUtil.fastSimpleUUID());
    }
}
