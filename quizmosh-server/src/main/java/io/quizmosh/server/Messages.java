package io.quizmosh.server;

import java.util.*;

/** UTF-8 message bundles; the caller chooses UI language, independently of room content. */
public final class Messages {
    private Messages() {}
    public static String text(String code,Locale requested,Map<String,Object> arguments) {
        Locale locale=requested!=null && requested.getLanguage().equals("en")?Locale.ENGLISH:Locale.forLanguageTag("pt-BR");
        ResourceBundle bundle=ResourceBundle.getBundle("messages",locale,ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        String result=bundle.containsKey(code)?bundle.getString(code):bundle.getString("error.unavailable");
        for(var arg:arguments.entrySet()) result=result.replace("{"+arg.getKey()+"}",String.valueOf(arg.getValue()));
        return result;
    }
    public static Map<String,Object> body(String code,Locale locale,Map<String,Object> arguments) {
        return Map.of("code",code,"arguments",arguments,"message",text(code,locale,arguments));
    }
}
