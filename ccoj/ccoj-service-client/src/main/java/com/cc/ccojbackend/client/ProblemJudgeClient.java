package com.cc.ccojbackend.client;

import com.cc.ccojbackend.model.submission.JudgeOutcome;
import com.cc.ccojbackend.model.submission.JudgeTask;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;

@HttpExchange("/inner/submissions")
public interface ProblemJudgeClient {
    @PostExchange("/{id}/claim")
    JudgeTask claim(@PathVariable("id") long id, @RequestParam("attempt") int attempt);

    @PutExchange("/{id}/outcome")
    void finish(@PathVariable("id") long id, @RequestParam("attempt") int attempt, @RequestBody JudgeOutcome outcome);
    @PostExchange("/{id}/retry")
    void retry(@PathVariable("id") long id, @RequestParam("attempt") int attempt);
}
