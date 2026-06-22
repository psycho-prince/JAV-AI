package com.javai.llm;

import com.javai.models.Message;
import java.util.List;

public class LLMRequest {
    private List<Message> messages;
    private double temperature = 0.7;

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}
