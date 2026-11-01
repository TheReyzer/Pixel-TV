package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class WifiRecentNetworksTest {
    @Test public void keepsFiveMostRecentDistinctConnections() {
        String state="[]";
        for (String name:Arrays.asList("A","B","C","D","E","F","C")) state=WifiRecentNetworks.connected(state,name);
        assertEquals(Arrays.asList("C","F","E","D","B"),WifiRecentNetworks.read(state));
    }
    @Test public void ignoresUnknownConnectionsAndPreservesNames() {
        String state=WifiRecentNetworks.connected("[]","\"Minha Wi-Fi\"");
        assertEquals(state,WifiRecentNetworks.connected(state,"<unknown ssid>"));
        assertEquals(state,WifiRecentNetworks.connected(state,null));
        assertEquals(Arrays.asList("Minha Wi-Fi"),WifiRecentNetworks.read(state));
    }
    @Test public void recoversMalformedHistoryAndDeduplicates() {
        assertEquals(Arrays.asList("Rede"),WifiRecentNetworks.read(WifiRecentNetworks.connected("broken","Rede")));
        assertEquals(Arrays.asList("A","B"),WifiRecentNetworks.read("[\"A\",\"A\",\"0x\",\"B\"]"));
    }
}
