package com.cholog.bootcamp.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatPageController {

    @GetMapping({"/", "/chat"})
    public String chat(Model model) {
        model.addAttribute("pageTitle", "초록 고객지원 챗봇");
        model.addAttribute("initialMessage", "안녕하세요. 초록 고객지원 챗봇입니다. 배송, 반품, 멤버십, 결제 관련 질문을 물어보세요.");
        model.addAttribute("quickPrompts", List.of(
            "배송은 보통 얼마나 걸리나요?",
            "반품 신청 기준을 알려주세요.",
            "멤버십 등급 혜택이 궁금해요."
        ));
        return "chat";
    }
}
