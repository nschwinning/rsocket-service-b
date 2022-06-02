package com.eon.demo;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;

import com.github.javafaker.Faker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootApplication
public class RsocketServiceBApplication {

	public static void main(String[] args) {
		Hooks.onErrorDropped(e -> log.info("Client disconnected, goodbye!"));
		SpringApplication.run(RsocketServiceBApplication.class, args);
	}

}

@EnableScheduling
@Configuration
class SchedulingConfiguration {
	
}

@Slf4j
@Configuration
class FlywayConfiguration {
	
	private final Environment env;

    public FlywayConfiguration(final Environment env) {
        this.env = env;
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway() {
        var url = env.getRequiredProperty("spring.flyway.url");
        var user = env.getRequiredProperty("spring.flyway.user");
        var password = env.getRequiredProperty("spring.flyway.password");

        log.info("Configuring database with flyway for URL: " + url + ", user: " + user);

        return new Flyway(Flyway.configure().dataSource(url, user, password));
    }
	
}

@Configuration
@EnableRSocketSecurity
class SecurityConfiguration {
	
	@Bean
	public RSocketMessageHandler messageHandler(RSocketStrategies strategies) {
		RSocketMessageHandler mh = new RSocketMessageHandler();
		mh.getArgumentResolverConfigurer().addCustomResolver(new AuthenticationPrincipalArgumentResolver());
		mh.setRSocketStrategies(strategies);
		return mh;
	}
	
	@Bean
	public ReactiveUserDetailsService userDetailsService() {
		final UserDetails user = User.withUsername("user")
				.password(passwordEncoder().encode("password"))
				.roles("USER")
				.build();
		return new MapReactiveUserDetailsService(user);
	}
	
	@Bean
	public PayloadSocketAcceptorInterceptor authorization(RSocketSecurity rsocket) {
		return rsocket
				.authorizePayload(authorize -> authorize
						.route("quoteById").authenticated()
						.anyExchange().permitAll())
				.simpleAuthentication(Customizer.withDefaults())
				.build();
	}
	
	@Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
	
}

@Slf4j
@Component
@RequiredArgsConstructor
class QuoteGenerator {
	
	private final QuoteRepository quoteRepository;
	private Faker faker = new Faker(Locale.GERMAN);
	private final KafkaTemplate<String,QuoteDto> kafkaTemplate;
	
	@Scheduled(fixedDelay = 60000)
	public void createQuote() {
		
		var quote = new Quote(faker.chuckNorris().fact());
		quoteRepository.save(quote)
			.subscribe(q -> {
				log.info("Created quote with id " + q.getId());
				kafkaTemplate.send("quotes", new QuoteDto(q.getId()));
			});
		
	}
	
}

@Slf4j
@RequiredArgsConstructor
@Controller
class QuoteController {
	
	private Faker faker = new Faker(Locale.GERMAN);
	private final QuoteRepository quoteRepository;
	
	//Request Stream
	@MessageMapping("quotes")
	public Flux<Quote> getQuotes(int boundary) {
		return Flux.range(1, boundary)
				.map(i -> new Quote(i, faker.chuckNorris().fact(), LocalDateTime.now()))
				.delayElements(Duration.ofSeconds(1));
	}
	
	//Request/Response
	@MessageMapping("quote")
	public Mono<Quote> getQuote() {
		Quote quote = new Quote(1, faker.chuckNorris().fact(), LocalDateTime.now());
		return Mono.just(quote).delayElement(Duration.ofSeconds(5));
	}
	
	@MessageMapping("quoteById")
	public Mono<Quote> getQuoteById(long id) {
		return quoteRepository.findById(id);
	}
	 
	//Fire and Forget
	@MessageMapping("log")
	public Mono<Void> log(Instant timestamp) {
		log.info("Time: " + timestamp);
		return Mono.empty();
	}
}

@Repository
interface QuoteRepository extends ReactiveCrudRepository<Quote, Long> {
	
}

@NoArgsConstructor
@AllArgsConstructor
@Data
class Quote {
	
	@Id
	private Integer id;
	private String message;
	private LocalDateTime createdAt;
	
	public Quote(String message) {
		this.message=message;
		this.createdAt=LocalDateTime.now();
	}

}

@AllArgsConstructor
@Data
class QuoteDto {
	
	private Integer id;
	
}