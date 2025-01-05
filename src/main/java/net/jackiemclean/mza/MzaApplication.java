package net.jackiemclean.mza;

import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@SpringBootApplication
@EntityScan(basePackages = "net.jackiemclean.mza")
@ConfigurationPropertiesScan("net.jackiemclean.mza")
public class MzaApplication {
  public static void main(String[] args) {
    SpringApplication.run(MzaApplication.class, args);
  }

  @Bean
  public DataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.sqlite.JDBC");
    dataSource.setUrl("jdbc:sqlite:${spring.datasource.url}");
    return dataSource;
  }
}
