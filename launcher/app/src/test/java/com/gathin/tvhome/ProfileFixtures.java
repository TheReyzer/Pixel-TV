package com.gathin.tvhome;
final class ProfileFixtures {
    static ViewerProfiles household() {
        try { return ViewerProfiles.fromJson("{\"version\":5,\"selected\":\"general\",\"profiles\":[{\"id\":\"general\",\"name\":\"Geral\",\"avatar\":\"lion\"},{\"id\":\"one\",\"name\":\"Pessoa Um\",\"avatar\":\"fox\"},{\"id\":\"two\",\"name\":\"Pessoa Dois\",\"avatar\":\"cat\"},{\"id\":\"three\",\"name\":\"Pessoa Três\",\"avatar\":\"panda\"}]}"); }
        catch (Exception error) { throw new AssertionError(error); }
    }
}
