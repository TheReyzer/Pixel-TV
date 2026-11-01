package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class FirstProfileTest {
    @Test public void freshInstallHasOnlyGeneralAndRequiresAnIndividual() throws Exception {
        ViewerProfiles model=new ViewerProfiles(); assertEquals(1,model.profiles().size()); assertEquals(ViewerProfiles.GENERAL,model.selected().id); assertTrue(model.needsIndividualProfile());
        assertTrue(ViewerProfiles.fromJson(model.toJson()).needsIndividualProfile());
        assertThrows(IllegalArgumentException.class,() -> model.createProfile(" ","cat",0)); assertTrue(model.needsIndividualProfile());
        model.createProfile("Minha conta","cat",2); assertFalse(model.needsIndividualProfile()); assertFalse(ViewerProfiles.fromJson(model.toJson()).needsIndividualProfile());
    }
    @Test public void existingHouseholdsArePreservedOnUpgrade() throws Exception {
        ViewerProfiles model=ProfileFixtures.household(); model.select("two"); String saved=model.toJson();
        ViewerProfiles loaded=ViewerProfiles.fromJson(saved); assertEquals(saved,loaded.toJson()); assertEquals(4,loaded.profiles().size()); assertFalse(loaded.needsIndividualProfile());
    }
}
