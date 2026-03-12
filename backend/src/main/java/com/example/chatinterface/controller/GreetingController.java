package com.example.chatinterface.controller;

import com.example.chatinterface.model.Greeting;
import com.example.chatinterface.service.GreetingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping
    public List<Greeting> getAllGreetings() {
        return greetingService.getAllGreetings();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Greeting createGreeting(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        return greetingService.createGreeting(message);
    }
}
