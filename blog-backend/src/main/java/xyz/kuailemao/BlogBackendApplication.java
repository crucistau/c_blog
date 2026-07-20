package xyz.kuailemao;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@MapperScan("xyz.kuailemao.mapper")
@SpringBootApplication
@EnableMethodSecurity
@Slf4j
public class BlogBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogBackendApplication.class, args);

        log.info(
                """
                \n
                ---------------------------------------------------------恭喜你成功启动后端---------------------------------------------------------
                        _________                              ___.   .__
                        \\_   ___ \\_______ __ _____  ___        \\_ |__ |  |   ____   ____
                        /    \\  \\/\\_  __ \\  |  \\  \\/  /  ______ | __ \\|  |  /  _ \\ / ___\\
                        \\     \\____|  | \\/  |  />    <  /_____/ | \\_\\ \\  |_(  <_> ) /_/  >
                         \\______  /|__|  |____//__/\\_ \\         |___  /____/\\____/\\___  /
                                \\/                   \\/             \\/           /_____/

                """
        );
    }
}
