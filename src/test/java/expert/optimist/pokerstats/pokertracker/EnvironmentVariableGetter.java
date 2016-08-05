package expert.optimist.pokerstats.pokertracker;

import java.util.Map;

public class EnvironmentVariableGetter {

    public static String getEnv(String envName) {
        Map<String, String> env = System.getenv();
        for (String name : env.keySet()) {
            if (name.equalsIgnoreCase(envName)) {
                return env.get(envName);
            }
        }
        return null;
    }

}
