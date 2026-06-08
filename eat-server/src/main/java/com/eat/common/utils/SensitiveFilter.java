package com.eat.common.utils;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SensitiveFilter {

    private final TrieNode root = new TrieNode();

    private static final Set<String> DEFAULT_SENSITIVE_WORDS = Set.of(
            "绝食", "催吐", "暴食", "辟谷", "断食疗法", "排毒果汁",
            "一周只吃苹果", "一天一顿", "极端断食", "呕吐减肥",
            "毒品", "吸毒"
    );

    @PostConstruct
    public void init() {
        DEFAULT_SENSITIVE_WORDS.forEach(this::addWord);
    }

    public void addWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node.children.computeIfAbsent(c, k -> new TrieNode());
            node = node.children.get(c);
        }
        node.isEnd = true;
    }

    public boolean containsSensitive(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            TrieNode node = root;
            for (int j = i; j < text.length(); j++) {
                node = node.children.get(text.charAt(j));
                if (node == null) break;
                if (node.isEnd) return true;
            }
        }
        return false;
    }

    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEnd;
    }
}
