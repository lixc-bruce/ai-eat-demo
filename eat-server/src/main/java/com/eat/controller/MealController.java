package com.eat.controller;

import com.eat.common.R;
import com.eat.dto.request.MealGenerateRequest;
import com.eat.dto.request.MealRegenerateRequest;
import com.eat.dto.response.MealPlanResponse;
import com.eat.dto.response.MealResultResponse;
import com.eat.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1/meal")
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@RequestAttribute("userId") Long userId,
                                @RequestBody @Valid MealGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(30_000L);

        CompletableFuture.runAsync(() -> {
            try {
                MealResultResponse result = mealService.generate(userId, request);
                for (MealPlanResponse plan : result.getPlans()) {
                    emitter.send(SseEmitter.event()
                            .name("plan")
                            .data(plan));
                }
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of("totalPlans", result.getPlans().size(),
                                "fromFallback", result.isFromFallback())));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("code", 500, "message", e.getMessage())));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@RequestAttribute("userId") Long userId,
                                  @RequestBody @Valid MealRegenerateRequest request) {
        SseEmitter emitter = new SseEmitter(30_000L);

        CompletableFuture.runAsync(() -> {
            try {
                MealPlanResponse plan = mealService.regenerate(userId, request);
                if (plan != null) {
                    emitter.send(SseEmitter.event()
                            .name("plan")
                            .data(plan));
                }
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of("replaced", plan != null)));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("code", 500, "message", e.getMessage())));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    @GetMapping("/fallback")
    public R<Map<String, List<MealPlanResponse>>> fallback() {
        return R.ok(mealService.getFallback());
    }
}
