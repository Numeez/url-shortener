package com.example.url_shortener.common.exception;

public class AliasAlreadyExistsException extends RuntimeException {

    public AliasAlreadyExistsException(String alias) {
        super("Custom alias '" + alias + "' is already taken");
    }
}
