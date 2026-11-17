package org.chenile.configuration.scheduler.db.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.chenile.base.response.GenericResponse;
import org.chenile.http.annotation.ChenileController;
import org.chenile.http.handler.ControllerSupport;
import org.chenile.scheduler.db.SchedulerDefinitionEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ChenileController(
    value = "scheduleService",
    serviceName = "_scheduleService_",
    bluePrintName = "chenile-service")
public class ScheduleController extends ControllerSupport {
  @PostMapping("/schedule")
  public ResponseEntity<GenericResponse<SchedulerDefinitionEntity>> save(
      HttpServletRequest request, @RequestBody SchedulerDefinitionEntity schedule) {
    return process(request, schedule);
  }

  @GetMapping("/schedule/{id}")
  public ResponseEntity<GenericResponse<SchedulerDefinitionEntity>> retrieve(
      HttpServletRequest request, @PathVariable("id") String id) {
    return process(request, id);
  }

  @GetMapping("/schedule")
  public ResponseEntity<GenericResponse<List<SchedulerDefinitionEntity>>> findAll(
      HttpServletRequest request) {
    return process(request);
  }

  @DeleteMapping("/schedule/{id}")
  public ResponseEntity<GenericResponse<Void>> delete(
      HttpServletRequest request, @PathVariable("id") String id) {
    return process(request, id);
  }
}
