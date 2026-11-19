package com.cc.ccojbackend.problem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc.ccojbackend.common.auth.AuthCheck;
import com.cc.ccojbackend.common.auth.CurrentUser;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.Result;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.problem.mapper.SubmissionMapper;
import com.cc.ccojbackend.problem.model.entity.Submission;
import com.cc.ccojbackend.problem.service.SubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/submission")
@RequiredArgsConstructor
public class SubmissionController {
    private final SubmissionService service;
    private final SubmissionMapper submissions;

    public record CreateSubmission(@NotNull @Positive Long problemId, @NotBlank String language,
                                   @NotBlank @Size(max = 65536) String sourceCode) {}

    public record Item(Long id, Long problemId, Long userId, String status) {
        static Item from(Submission submission) {
            return new Item(submission.getId(), submission.getProblemId(), submission.getUserId(),
                    submission.getStatus());
        }
    }

    public record SubmissionPage(List<Item> records, long total) {}

    @AuthCheck
    @PostMapping("/judge")
    public Result<Long> submit(@Valid @RequestBody CreateSubmission input, HttpServletRequest request) {
        return Result.success(service.submit(input.problemId(), CurrentUser.require(request).userId(),
                input.language(), input.sourceCode()));
    }

    @AuthCheck
    @GetMapping("/{id}")
    public Result<Submission> get(@PathVariable Long id, HttpServletRequest request) {
        var user = CurrentUser.require(request);
        var submission = submissions.selectById(id);
        if (submission == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!"ADMIN".equals(user.userRole()) && !user.userId().equals(submission.getUserId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }
        return Result.success(submission);
    }

    @AuthCheck
    @GetMapping
    public Result<SubmissionPage> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException(ResultCode.PARAMS_ERROR);
        var user = CurrentUser.require(request);
        var query = new LambdaQueryWrapper<Submission>()
                .eq(!"ADMIN".equals(user.userRole()), Submission::getUserId, user.userId());
        long total = submissions.selectCount(query);
        var records = submissions.selectList(query
                .select(Submission::getId, Submission::getProblemId, Submission::getUserId, Submission::getStatus)
                .orderByDesc(Submission::getId).last("LIMIT " + size + " OFFSET " + ((page - 1L) * size)));
        return Result.success(new SubmissionPage(records.stream().map(Item::from).toList(), total));
    }
}
