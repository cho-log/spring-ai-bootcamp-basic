package com.cholog.bootcamp.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int topK = 5;
    private String faqSplitRegex = "(?m)^### ";
    private String policySplitRegex = "(?m)^## ";

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public String getFaqSplitRegex() {
        return faqSplitRegex;
    }

    public void setFaqSplitRegex(String faqSplitRegex) {
        this.faqSplitRegex = faqSplitRegex;
    }

    public String getPolicySplitRegex() {
        return policySplitRegex;
    }

    public void setPolicySplitRegex(String policySplitRegex) {
        this.policySplitRegex = policySplitRegex;
    }
}
