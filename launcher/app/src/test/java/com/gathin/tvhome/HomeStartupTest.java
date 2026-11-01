package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeStartupTest {
 @Test public void cachedCoversReleaseTheWelcomeWithoutArtificialWaiting(){assertTrue(MainActivity.startupCanReveal(true,true,0));}
 @Test public void initialContentIsRequiredBeforeRevealingHome(){assertFalse(MainActivity.startupCanReveal(false,true,99999));assertFalse(MainActivity.startupCanReveal(false,false,99999));}
 @Test public void offlineArtworkDoesNotBlockAnOtherwiseReadyHomeForever(){assertFalse(MainActivity.startupCanReveal(true,false,3999));assertTrue(MainActivity.startupCanReveal(true,false,4000));}
}
