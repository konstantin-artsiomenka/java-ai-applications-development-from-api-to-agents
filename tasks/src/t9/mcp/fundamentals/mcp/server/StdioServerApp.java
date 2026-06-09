package t9.mcp.fundamentals.mcp.server;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class StdioServerApp {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(StdioServerApp.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "spring.ai.mcp.server.stdio", "true",
                "spring.ai.mcp.server.name", "ums-mcp-server",
                "spring.ai.mcp.server.version", "1.0.0",
                "logging.level.root", "OFF"
        ));
        app.run(args);
    }
}
