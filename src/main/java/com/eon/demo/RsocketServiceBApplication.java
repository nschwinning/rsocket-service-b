package com.eon.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

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

@Slf4j
@RequiredArgsConstructor
@Controller
class QuoteController {
	
	private Faker faker = new Faker(Locale.GERMAN);
	
	//Request Stream
	@MessageMapping("quotes")
	public Flux<Quote> getQuotes(int boundary) {
		return Flux.range(1, boundary)
				.map(i -> new Quote(i, faker.chuckNorris().fact()))
				.delayElements(Duration.ofSeconds(1));
	}
	
	//Request/Response
	@MessageMapping("quote")
	public Mono<Quote> getQuote() {
		Quote quote = new Quote(1, faker.chuckNorris().fact());
		return Mono.just(quote).delayElement(Duration.ofSeconds(5));
	}
	 
	//Fire and Forget
	@MessageMapping("log")
	public Mono<Void> log(Instant timestamp) {
		log.info("Time: " + timestamp);
		return Mono.empty();
	}
}

@NoArgsConstructor
@AllArgsConstructor
@Data
class Quote {
	private Integer quoteId; 
	private String message;
}