package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WatchNavigationTest {
    @Test public void recognizesProfileSelectionWithoutChoosingAProfile() {
        assertTrue(WatchNavigation.isProfilePrompt("Quem está assistindo?"));
        assertTrue(WatchNavigation.isProfilePrompt("Who's watching?"));
        assertTrue(WatchNavigation.isProfilePrompt("Selecione seu perfil"));
        assertFalse(WatchNavigation.isProfilePrompt("Unabomber"));
    }

    @Test public void doesNotTreatLoginOrPinAsSearchFields() {
        assertTrue(WatchNavigation.isAccountGate("Digite seu PIN"));
        assertTrue(WatchNavigation.isAccountGate("Senha"));
        assertTrue(WatchNavigation.isAccountGate("Sign in"));
        assertFalse(WatchNavigation.isAccountGate("Pesquisar"));
    }

    @Test public void matchesAccentsAndOptionalYearButRejectsSequelsAndHomonymYears() {
        assertTrue(WatchNavigation.titleMatches("Ainda Estou Aqui", "AINDA ESTOU AQUI", "2024"));
        assertTrue(WatchNavigation.titleMatches("Chacal (2024)", "Chacal", "2024"));
        assertFalse(WatchNavigation.titleMatches("Chacal (1997)", "Chacal", "2024"));
        assertFalse(WatchNavigation.titleMatches("Duna: Parte Dois", "Duna", "2021"));
        assertFalse(WatchNavigation.titleMatches("Buscar Unabomber", "Unabomber", "2026"));
    }
}
