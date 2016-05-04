package expert.optimist.pokerstats.pokertracker.player.boundary;


import com.airhacks.rulz.jaxrsclient.JAXRSClientProvider;
import org.junit.Rule;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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

    @Rule
    public JAXRSClientProvider provider = buildWithURI("http://localhost:8080/pokertracker/resources/players");

    @Test
    public void crud() {
        String firstName = "Robert";
        String lastName = "Brem";
        String firstNameUpdated = firstName + "updated";
        String lastNameUpdated = lastName + "updated";

        JsonObjectBuilder playerBuilder = Json.createObjectBuilder();
        JsonObject playerToCreate = playerBuilder.
                add(FIRST_NAME, firstName).
                add(LAST_NAME, lastName).
                build();

        //create
        Response postResponse = this.provider.target().request().
                post(Entity.json(playerToCreate));
        assertThat(postResponse.getStatus(), is(201));
        String location = postResponse.getHeaderString(LOCATION);

        //find
        JsonObject dedicatedPlayer = this.provider.client().
                target(location).
                request(MediaType.APPLICATION_JSON).
                get(JsonObject.class);
        assertThat(dedicatedPlayer.keySet(), hasItem(FIRST_NAME));
        assertThat(dedicatedPlayer.keySet(), hasItem(LAST_NAME));
        assertTrue(dedicatedPlayer.getString(FIRST_NAME).contains(firstName));
        assertTrue(dedicatedPlayer.getString(LAST_NAME).contains(lastName));

        //update
        JsonObjectBuilder updateBuilder = Json.createObjectBuilder();
        JsonObject updated = updateBuilder.
                add(FIRST_NAME, firstNameUpdated).
                add(LAST_NAME, lastNameUpdated).
                build();

        Response updateResponse = this.provider.client().
                target(location).
                request(MediaType.APPLICATION_JSON)
                .put(Entity.json(updated));
        assertThat(updateResponse.getStatus(), is(200));

        //find it again
        JsonObject updatedPlayer = this.provider.client().
                target(location).
                request(MediaType.APPLICATION_JSON).
                get(JsonObject.class);
        assertTrue(updatedPlayer.getString(FIRST_NAME).contains(firstNameUpdated));

        //findAll
        Response response = this.provider.target().
                request(MediaType.APPLICATION_JSON).
                get();
        assertThat(response.getStatus(), is(200));
        JsonArray allPlayers = response.readEntity(JsonArray.class);
        assertFalse(allPlayers.isEmpty());
        assertThat(allPlayers, hasItem(updatedPlayer));

        //deleting not-existing
        Response deleteResponse = this.provider.target().
                path("-42").
                request(MediaType.APPLICATION_JSON).delete();
        assertThat(deleteResponse.getStatus(), is(204));
    }

}

