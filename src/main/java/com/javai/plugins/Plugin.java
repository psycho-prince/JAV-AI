package com.javai.plugins;

public interface Plugin {
    String getName();
    String getDescription();
    String execute(String[] args) throws Exception;
}
