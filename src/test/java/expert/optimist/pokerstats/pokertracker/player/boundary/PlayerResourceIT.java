package expert.optimist.pokerstats.pokertracker.player.boundary;


import com.airhacks.rulz.jaxrsclient.JAXRSClientProvider;
import expert.optimist.pokerstats.pokertracker.EnvironmentVariableGetter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.adapters.HttpClientBuilder;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.constants.ServiceUrlConstants;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.util.JsonSerialization;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.airhacks.rulz.jaxrsclient.JAXRSClientProvider.buildWithURI;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerResourceIT {
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";
    public static final String LOCATION = "Location";
    public static final String PRIVATE_KEY_LOCATION = "PRIVATE_KEY_LOCATION";

    public static String PRIVATE_KEY_PATH;

    @Rule
    public JAXRSClientProvider commandProvider = buildWithURI("http://5.189.172.129:8282/pokertracker/resources/players");

    @Rule
    public JAXRSClientProvider queryProvider = buildWithURI("http://5.189.172.129:8383/pokertracker/resources/players");

    @BeforeClass
    public static void testSetup() throws Exception {
        PRIVATE_KEY_PATH = EnvironmentVariableGetter.getEnv(PRIVATE_KEY_LOCATION);

        copyFileToServer("/keycloak/Dockerfile", "keycloak");
        copyFileToServer("/keycloak/standalone.xml", "keycloak");

        copyFileToServer("/pokertracker-command/Dockerfile", "pokertracker-command");
        copyFileToServer("/pokertracker-command/pokertracker.war", "pokertracker-command");

        copyFileToServer("/pokertracker-query/Dockerfile", "pokertracker-query");
        copyFileToServer("/pokertracker-query/pokertracker-query.war", "pokertracker-query");

        copyFileToServer("/initial_db.sql", "");

        URL startScript = PlayerResourceIT.class.getResource("/start_container.sh");
        String startScriptPath = startScript.getPath();
        runRemoteScript(startScriptPath + " " + escape("0.0.1"));
    }

    public static void copyFileToServer(String relativePath, String destinationFolder) throws Exception {
        URL fileUrl = PlayerResourceIT.class.getResource(relativePath);
        String filePath = fileUrl.getPath();
        runCommand("scp -i " + PRIVATE_KEY_PATH + " " + filePath + " root@5.189.172.129:/root/" + destinationFolder);
    }

    public static void runRemoteScript(String startScriptPath) throws IOException, InterruptedException {
        String startCommand = getRemotePreString(EnvironmentVariableGetter.getEnv(PRIVATE_KEY_LOCATION), "root", "5.189.172.129") +
                "'bash -s' < " + startScriptPath;
        System.out.println("startCommand = " + startCommand);
        runCommand(startCommand);
    }

    public static String getRemotePreString(String privateKeyLocation, String remoteUser, String remoteAddress) {
        return "ssh -i " + privateKeyLocation + " " + remoteUser + "@" + remoteAddress + " ";
    }

    public static String escape(String toEscape) {
        return "'" + toEscape.replaceAll("/", "\\\\/") + "'";
    }

    public static void runCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "/bin/bash", "-c",
                command);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        p.waitFor();
    }

    @Test
    public void crud() throws Exception {
        String firstName = "Robert";
        String lastName = "Brem";
        String firstNameUpdated = firstName + "updated";
        String lastNameUpdated = lastName + "updated";

        JsonObjectBuilder playerBuilder = Json.createObjectBuilder();
        JsonObject playerToCreate = playerBuilder
                .add(FIRST_NAME, firstName)
                .add(LAST_NAME, lastName)
                .build();

        // create
        Response postResponse = this.commandProvider.target().request()
                .header("Authorization", "Bearer " + getTokenResponse("admin", "admin").getToken())
                .post(Entity.json(playerToCreate));
        assertThat(postResponse.getStatus(), is(201));
        String location = postResponse.getHeaderString(LOCATION);
        String commandLocation = location.replace("pokertracker-query", "pokertracker");

        // find
        JsonObject dedicatedPlayer = this.queryProvider.client()
                .target(location)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getTokenResponse("test", "1234").getToken())
                .get(JsonObject.class);
        assertThat(dedicatedPlayer.keySet(), hasItem(FIRST_NAME));
        assertThat(dedicatedPlayer.keySet(), hasItem(LAST_NAME));
        assertTrue(dedicatedPlayer.getString(FIRST_NAME).contains(firstName));
        assertTrue(dedicatedPlayer.getString(LAST_NAME).contains(lastName));

        // update
        JsonObjectBuilder updateBuilder = Json.createObjectBuilder();
        JsonObject updated = updateBuilder
                .add(FIRST_NAME, firstNameUpdated)
                .add(LAST_NAME, lastNameUpdated)
                .build();

        Response updateResponse = this.commandProvider.client()
                .target(commandLocation)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getTokenResponse("admin", "admin").getToken())
                .put(Entity.json(updated));
        assertThat(updateResponse.getStatus(), is(200));

        // find it again
        JsonObject updatedPlayer = this.queryProvider.client()
                .target(location)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getTokenResponse("test", "1234").getToken())
                .get(JsonObject.class);
        assertTrue(updatedPlayer.getString(FIRST_NAME).contains(firstNameUpdated));

        // findAll
        Response response = this.queryProvider.target()
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getTokenResponse("test", "1234").getToken())
                .get();
        assertThat(response.getStatus(), is(200));
        JsonArray allPlayers = response.readEntity(JsonArray.class);
        assertFalse(allPlayers.isEmpty());
        assertThat(allPlayers, hasItem(updatedPlayer));

        // deleting not-existing
        Response deleteResponse = this.commandProvider.target()
                .path("-42")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getTokenResponse("admin", "admin").getToken())
                .delete();
        assertThat(deleteResponse.getStatus(), is(204));

        // delete
        Response deleteCreatedResponse = this.commandProvider
                .target()
                .path(String.valueOf(updatedPlayer.getInt("id")))
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + getTokenResponse("admin", "admin").getToken())
                .delete();
        assertThat(deleteResponse.getStatus(), is(204));

    }

    private AccessTokenResponse getTokenResponse(String user, String password) throws ClientProtocolException, IOException {
        HttpClient client = new HttpClientBuilder().disableTrustManager().build();
        try {
            HttpPost post = new HttpPost(KeycloakUriBuilder.fromUri("https://5.189.172.129:8443/auth")
                    .path(ServiceUrlConstants.TOKEN_PATH).build("pokerstats"));
            List<NameValuePair> formparams = new ArrayList<>();
            formparams.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, "password"));
            formparams.add(new BasicNameValuePair("username", user));
            formparams.add(new BasicNameValuePair("password", password));

            formparams.add(new BasicNameValuePair(OAuth2Constants.CLIENT_ID, "pokerui"));


            UrlEncodedFormEntity form = new UrlEncodedFormEntity(formparams, "UTF-8");
            post.setEntity(form);
            HttpResponse response = client.execute(post);
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            if (status != 200) {
                throw new IOException("Bad status: " + status);
            }
            if (entity == null) {
                throw new IOException("No Entity");
            }
            InputStream is = entity.getContent();
            try {
                AccessTokenResponse tokenResponse = JsonSerialization.readValue(is, AccessTokenResponse.class);
                return tokenResponse;
            } finally {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}

