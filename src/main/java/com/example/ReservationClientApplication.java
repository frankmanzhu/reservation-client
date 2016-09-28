package com.example;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.http.HttpMethod;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.MessageChannel;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

//This is really important, otherwise Feign is broken
@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
@EnableBinding(ReservationChannels.class)
@EnableFeignClients
@EnableDiscoveryClient
@EnableZuulProxy
@EnableCircuitBreaker
@SpringBootApplication
@IntegrationComponentScan
@EnableResourceServer
public class ReservationClientApplication {

    @Bean
    @LoadBalanced
    RestTemplate restTemplate() {
        return new RestTemplate();
    }
    public static void main(String[] args) {
        SpringApplication.run(ReservationClientApplication.class, args);
    }
}

interface ReservationChannels {

    @Output
    public MessageChannel output();
}

@FeignClient("http://reservation-service")
interface ReservationReader {

	@RequestMapping(method = RequestMethod.GET, value = "/reservations")
    Resources<Reservation> read();
}

@MessagingGateway
interface ReservationWriter {

    @Gateway(requestChannel = "output")
    void write(String rn);
}


class Reservation {
	private String reservationName;

	public String getReservationName() {
		return reservationName;
	}

}


@RestController
@RequestMapping("/reserve")
class ReservationApiGateway {

	private final ReservationReader reservationReader;

    private final ReservationWriter reservationWriter;

    private final RestTemplate restTemplate;

    @Autowired
    public ReservationApiGateway(ReservationReader reservationReader, ReservationWriter reservationWriter, RestTemplate restTemplate) {
        this.reservationReader = reservationReader;
        this.reservationWriter = reservationWriter;
        this.restTemplate = restTemplate;
    }

    @RequestMapping(method = RequestMethod.POST)
    public void write(@RequestBody Reservation reservation) {
        this.reservationWriter.write(reservation.getReservationName());
    }

    public Collection<String> fallback() {
        return Collections.emptyList();
    }

    @HystrixCommand(fallbackMethod = "fallback")
	@RequestMapping(method = RequestMethod.GET, value = "/names")
	public Collection<String> names() {
        System.out.println(this.reservationReader.read().getContent());
		return this.reservationReader.read().getContent().stream()
                .map(Reservation::getReservationName)
                .collect(Collectors.toList());

	}

    @RequestMapping(method = RequestMethod.GET, value = "/names2")
    public Collection<String> names2() {
        return this
                .restTemplate
                .exchange("http://reservation-service/reservations",
                        HttpMethod.GET, null,
                        new ParameterizedTypeReference<Resources<Reservation>>() {
                        })
                .getBody()
                .getContent()
                .stream()
                .map(Reservation::getReservationName)
                .collect(Collectors.toList());
    }

}