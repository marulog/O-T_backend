package com.ott.transcoder;

import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.mq.InfraMqConfiguration;
import com.ott.infra.s3.config.InfraS3Configuration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
		InfraDbConfiguration.class,
		InfraS3Configuration.class,
		InfraMqConfiguration.class
})
public class TranscoderApplication {

	public static void main(String[] args) {
		SpringApplication.run(TranscoderApplication.class, args);
	}

}
