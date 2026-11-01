package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WatchNavigation {
    private static final Pattern MARKS=Pattern.compile("\\p{M}"),SEPARATORS=Pattern.compile("[^a-z0-9]+");
    static String normalize(String text) {
        String plain=MARKS.matcher(Normalizer.normalize(text==null?"":text,Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
        return SEPARATORS.matcher(plain).replaceAll(" ").trim();
    }

    static boolean isProfilePrompt(String text) {
        String value = normalize(text);
        return value.contains("quem esta assistindo") || value.contains("quem vai assistir")
                || value.contains("who s watching") || value.contains("who is watching")
                || value.contains("escolha o perfil") || value.contains("escolha seu perfil")
                || value.contains("selecione um perfil") || value.contains("selecione seu perfil")
                || value.contains("choose a profile") || value.contains("select a profile")
                || value.contains("gerenciar perfis") || value.contains("manage profiles");
    }

    static boolean isAccountGate(String text) {
        String value = normalize(text);
        return value.equals("senha") || value.equals("password") || value.equals("pin")
                || value.contains("digite seu pin") || value.contains("enter your pin")
                || value.equals("fazer login") || value.equals("sign in") || value.equals("entrar na conta")
                || value.contains("acesse sua conta") || value.contains("ativar dispositivo");
    }

    static boolean titleMatches(String result, String title, String year) {
        String found = normalize(result), expected = normalize(title);
        if (expected.isEmpty()) return false;
        if (found.equals(expected)) return true;
        Matcher expectedYear = Pattern.compile("(?:19|20)\\d{2}").matcher(year == null ? "" : year);
        if (!expectedYear.find()) return false;
        return found.equals(expected + " " + expectedYear.group());
    }
}
