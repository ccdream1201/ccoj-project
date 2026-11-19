package com.cc.ccojbackend.problem.controller;

import com.cc.ccojbackend.model.submission.JudgeOutcome;
import com.cc.ccojbackend.model.submission.JudgeTask;
import com.cc.ccojbackend.problem.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inner/submissions")
@RequiredArgsConstructor
public class SubmissionInnerController {
    private final SubmissionService service;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> failed(Exception exception) {
        org.slf4j.LoggerFactory.getLogger(SubmissionInnerController.class)
                .error("Internal submission operation failed", exception);
        return ResponseEntity.status(503).build();
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<JudgeTask> claim(@PathVariable long id, @RequestParam int attempt) {
        JudgeTask task = service.claim(id, attempt);
        return task == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(task);
    }

    @PutMapping("/{id}/outcome")
    public ResponseEntity<Void> finish(@PathVariable long id, @RequestParam int attempt, @RequestBody JudgeOutcome outcome) {
        service.finish(id, attempt, outcome);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable long id, @RequestParam int attempt) {
        service.scheduleRetry(id, attempt);
        return ResponseEntity.noContent().build();
    }
}
