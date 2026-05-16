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
        model.addAttribute("initialMessage", """
        안녕하세요. 초록 고객지원 챗봇입니다.🤖
        무엇을 도와드릴까요?
        
        📞 운영시간 안내
        자동 챗봇은 24시간 이용하실 수 있습니다.
        상담사 연결 및 전화 상담은 평일 오전 9시부터 오후 6시까지 가능하며, 주말 및 공휴일에는 운영되지 않습니다.
        운영 시간 외 문의는 챗봇을 이용하시거나 이메일로 남겨주시면 다음 영업일부터 순차적으로 확인해 드리겠습니다.
        
        ☎️ 문의 전화: 1588-0000
        📱 VIP 전용 전화: 1588-0002
        📧 이메일: support@cholog.kr
        💬 카카오톡: @초록
        """);
        model.addAttribute("quickPrompts", List.of(
            "배송은 보통 얼마나 걸리나요?",
            "반품 신청 기준을 알려주세요.",
            "멤버십 등급 혜택이 궁금해요."
        ));
        return "chat";
    }
}
