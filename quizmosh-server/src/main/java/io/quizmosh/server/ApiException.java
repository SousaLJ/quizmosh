package io.quizmosh.server;

import java.util.Map;

public final class ApiException extends RuntimeException {
    private final int status;
    private final Map<String,Object> arguments;
    public ApiException(int status,String code) {this(status,code,Map.of());}
    public ApiException(int status,String code,Map<String,Object> arguments) {
        super(code);this.status=status;this.arguments=Map.copyOf(arguments);
    }
    public int status() {return status;}
    public Map<String,Object> arguments() {return arguments;}
}
