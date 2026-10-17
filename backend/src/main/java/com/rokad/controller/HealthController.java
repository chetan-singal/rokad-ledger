package com.rokad.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class HealthController {

  @GetMapping("/api/health")
  public String health() {
    return "OK - ROKAD backend is running";
  }
}
