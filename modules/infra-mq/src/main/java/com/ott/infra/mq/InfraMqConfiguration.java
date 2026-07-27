package com.ott.infra.mq;

import com.ott.infra.mq.config.MqMessageConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(MqMessageConfig.class)
public class InfraMqConfiguration {
}
