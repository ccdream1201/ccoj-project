package com.cc.ccojbackend.problem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc.ccojbackend.client.UserDirectoryClient;
import com.cc.ccojbackend.common.auth.AuthCheck;
import com.cc.ccojbackend.common.auth.CurrentUser;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.Result;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.model.problem.ProblemCase;
import com.cc.ccojbackend.model.user.UserSummary;
import com.cc.ccojbackend.problem.model.dto.ProblemCreateRequest;
import com.cc.ccojbackend.problem.model.entity.Problem;
import com.cc.ccojbackend.problem.service.ProblemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
@Slf4j
@RequestMapping("/problem")
@RequiredArgsConstructor
public class ProblemController {
    private final ProblemService problems;
    private final JsonMapper jsonMapper;
    private final UserDirectoryClient userDirectory;

    public record PublishedProblem(Long id, String title, String description, Integer difficulty,
                                   List<ProblemCase> samples, Long createdBy) {}

    public record PublishedDetail(PublishedProblem problem, UserSummary author) {}

    public record PublishedPage(List<PublishedProblem> records, long total) {}

    @PostMapping("/add")
    @AuthCheck(mustRole = "ADMIN")
    public Result<Long> add(@Valid @RequestBody ProblemCreateRequest input, HttpServletRequest request) {
        return Result.success(problems.addProblem(input, CurrentUser.require(request).userId()));
    }

    @PutMapping("/{id}")
    @AuthCheck(mustRole = "ADMIN")
    public Result<Void> edit(@PathVariable Long id, @Valid @RequestBody ProblemCreateRequest input) {
        problems.editProblem(id, input);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @AuthCheck(mustRole = "ADMIN")
    public Result<Void> delete(@RequestParam Long id) {
        if (!problems.removeById(id)) throw new BusinessException(ResultCode.NOT_FOUND);
        return Result.success();
    }

    @PatchMapping("/{id}/publish")
    @AuthCheck(mustRole = "ADMIN")
    public Result<Void> publish(@PathVariable Long id) {
        if (!problems.lambdaUpdate().eq(Problem::getId, id).set(Problem::getStatus, 1).update()) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return Result.success();
    }

    @GetMapping("/admin/{id}")
    @AuthCheck(mustRole = "ADMIN")
    public Result<Problem> adminGet(@PathVariable Long id) {
        Problem problem = problems.getById(id);
        if (problem == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return Result.success(problem);
    }

    @GetMapping("/admin")
    @AuthCheck(mustRole = "ADMIN")
    public Result<List<Problem>> adminList(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(problems.list(new LambdaQueryWrapper<Problem>().orderByDesc(Problem::getId)
                .last(pageLimit(page, size))));
    }

    @GetMapping("/{id}")
    public Result<PublishedDetail> get(@PathVariable Long id) {
        Problem problem = problems.getById(id);
        if (problem == null || !Integer.valueOf(1).equals(problem.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return Result.success(new PublishedDetail(toPublic(problem), findAuthor(problem.getCreatedBy())));
    }

    @GetMapping
    public Result<PublishedPage> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var published = new LambdaQueryWrapper<Problem>().eq(Problem::getStatus, 1);
        long total = problems.count(published);
        var records = problems.list(published.orderByDesc(Problem::getId).last(pageLimit(page, size)));
        return Result.success(new PublishedPage(records.stream().map(this::toPublic).toList(), total));
    }

    private static String pageLimit(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException(ResultCode.PARAMS_ERROR);
        return "LIMIT " + size + " OFFSET " + ((page - 1L) * size);
    }

    private UserSummary findAuthor(Long userId) {
        if (userId == null) return null;
        try {
            var summary = userDirectory.get(userId);
            return summary != null && userId.equals(summary.userId()) ? summary : null;
        } catch (RestClientException exception) {
            log.warn("Could not load author of published problem, user id {}", userId);
            return null;
        }
    }

    private PublishedProblem toPublic(Problem problem) {
        List<ProblemCase> samples = jsonMapper.readValue(problem.getSamples(), new TypeReference<>() {});
        return new PublishedProblem(problem.getId(), problem.getTitle(), problem.getDescription(),
                problem.getDifficulty(), samples, problem.getCreatedBy());
    }
}
